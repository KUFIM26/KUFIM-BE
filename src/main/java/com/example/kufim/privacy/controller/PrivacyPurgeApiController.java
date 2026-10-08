package com.example.kufim.privacy.controller;

import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.privacy.entity.PrivacyPurgeLog;
import com.example.kufim.privacy.entity.PurgeTargetType;
import com.example.kufim.privacy.repository.PrivacyPurgeLogRepository;
import com.example.kufim.security.AdminAccess;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/privacy")
@RequiredArgsConstructor
public class PrivacyPurgeApiController {
    private final AdminAccess access;
    private final JdbcTemplate jdbc;
    private final PrivacyPurgeLogRepository logs;

    public record PurgeRequest(OffsetDateTime beforeAt, String confirmation) {}

    @PostMapping("/purge")
    @Transactional
    public ApiResponse<Map<String, Object>> purge(@RequestBody PurgeRequest request) {
        var actor = access.superAdmin();
        if (request.beforeAt() == null || !"DELETE_PERSONAL_DATA".equals(request.confirmation()) ||
                !request.beforeAt().isBefore(ApiClock.offset(ApiClock.now())))
            throw new ContractException(HttpStatus.BAD_REQUEST,"C001","파기 기준 시각과 확인 문구를 확인해 주세요.");
        var before = request.beforeAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        var ids = jdbc.queryForList("select id from waiting where status not in ('WAITING','CALLED') and closed_at < ? and student_number <> '000000000'", Long.class, before);
        for (var id : ids) {
            jdbc.update("delete from waiting_idempotency where waiting_id=?", id);
            jdbc.update("delete from notification_read where notification_id in (select id from notification where waiting_id=?)", id);
            jdbc.update("update notification set recipient_client_token='00000000-0000-0000-0000-000000000000' where waiting_id=?", id);
            jdbc.update("update waiting set student_number='000000000', phone_number='00000000000', client_token='00000000-0000-0000-0000-000000000000' where id=?", id);
        }
        for (var target : new PurgeTargetType[]{PurgeTargetType.STUDENT_NUMBER, PurgeTargetType.PHONE_NUMBER, PurgeTargetType.CLIENT_TOKEN})
            logs.save(PrivacyPurgeLog.builder().executedBy(actor).targetType(target).targetCount(ids.size())
                    .executedAt(ApiClock.now()).note("beforeAt=" + request.beforeAt()).build());
        return ApiResponse.ok(Map.of("purgedWaitingCount", ids.size(), "executedAt", ApiClock.offset(ApiClock.now()),
                "executedBy", actor.getId()));
    }
}
