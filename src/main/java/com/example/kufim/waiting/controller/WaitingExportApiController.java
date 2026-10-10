package com.example.kufim.waiting.controller;

import com.example.kufim.common.util.ApiClock;
import com.example.kufim.common.util.Csv;
import com.example.kufim.security.AdminAccess;
import com.example.kufim.waiting.repository.WaitingRepository;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 웨이팅 이력 내보내기. 학번·전화번호·이용자 토큰은 분석에 필요 없고 유출 경로만 늘리므로 넣지 않습니다(ADM-010). */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class WaitingExportApiController {
    private final AdminAccess access;
    private final WaitingRepository waitings;

    @GetMapping("/waitings.csv")
    @Operation(summary = "웨이팅 이력 CSV", description = "사후 분석용. UTF-8 BOM, 개인정보 제외, 기본 전체 기간.", tags = "관리자 · 분석")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(@RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to) {
        access.superAdmin();
        LocalDateTime start = from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : local(from);
        LocalDateTime end = to == null ? ApiClock.now() : local(to);
        var csv = new Csv("booth_id", "booth_name", "waiting_number", "party_size", "status", "registered_at",
                "called_at", "recall_count", "closed_at", "close_reason", "minutes_to_call");
        for (var w : waitings.export(start, end)) {
            Long toCall = w.getCalledAt() == null ? null : Duration.between(w.getRegisteredAt(), w.getCalledAt()).toMinutes();
            csv.row(w.getBooth().getId(), w.getBooth().getName(), w.getWaitingNumber(), (int) w.getPartySize(), w.getStatus(),
                    w.getRegisteredAt(), w.getCalledAt(), w.getRecallCount(), w.getClosedAt(), w.getCloseReason(), toCall);
        }
        return csv.download("kufim-waitings.csv");
    }

    private LocalDateTime local(OffsetDateTime time) { return time.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime(); }
}
