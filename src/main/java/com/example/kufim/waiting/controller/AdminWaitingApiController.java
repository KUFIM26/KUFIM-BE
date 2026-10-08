package com.example.kufim.waiting.controller;

import com.example.kufim.audit.entity.AuditLog;
import com.example.kufim.audit.repository.AuditLogRepository;
import com.example.kufim.booth.entity.AcceptingStatus;
import com.example.kufim.booth.repository.BoothRepository;
import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.common.util.PersonalDataMasker;
import com.example.kufim.security.AdminAccess;
import com.example.kufim.waiting.entity.WaitingStatus;
import com.example.kufim.waiting.repository.WaitingRepository;
import com.example.kufim.waiting.service.WaitingWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminWaitingApiController {
    private final WaitingWorkflowService workflow;
    private final WaitingRepository waitings;
    private final BoothRepository booths;
    private final AdminAccess access;
    private final AuditLogRepository audit;
    private final ObjectMapper json;

    public record ConfigPatch(AcceptingStatus acceptingStatus, @Min(1) @Max(360) Integer estimateMinutesPerTeam) {}

    @GetMapping("/booths/{boothId}/waiting-config")
    public ApiResponse<Map<String, Object>> config(@PathVariable long boothId) {
        access.booth(boothId);
        var b = booths.findByIdAndDeletedFalse(boothId).orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND,"B001","부스를 찾을 수 없습니다."));
        return ApiResponse.ok(Map.of("waitingEnabled", b.isWaitingEnabled(), "acceptingStatus", b.getAcceptingStatus(),
                "estimateMinutesPerTeam", b.getEstimateMinutesPerTeam(), "autoCancelMinutes", 10));
    }

    @PatchMapping("/booths/{boothId}/waiting-config")
    @Transactional
    public ApiResponse<Map<String, Object>> updateConfig(@PathVariable long boothId, @RequestBody JsonNode body) {
        access.booth(boothId);
        if (body.has("autoCancelMinutes") || body.has("waitingEnabled"))
            throw new ContractException(HttpStatus.BAD_REQUEST,"C001","이 설정은 변경할 수 없습니다.");
        ConfigPatch patch = json.treeToValue(body, ConfigPatch.class);
        if (patch.estimateMinutesPerTeam() != null && (patch.estimateMinutesPerTeam() < 1 || patch.estimateMinutesPerTeam() > 360))
            throw new ContractException(HttpStatus.BAD_REQUEST,"C001","예상 소요 시간을 확인해 주세요.");
        var b = booths.lockActive(boothId).orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND,"B001","부스를 찾을 수 없습니다."));
        b.configure(patch.acceptingStatus(), patch.estimateMinutesPerTeam());
        return ApiResponse.ok(Map.of("waitingEnabled", b.isWaitingEnabled(), "acceptingStatus", b.getAcceptingStatus(),
                "estimateMinutesPerTeam", b.getEstimateMinutesPerTeam(), "autoCancelMinutes", 10));
    }

    @GetMapping("/booths/{boothId}/waitings")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> list(@PathVariable long boothId, @RequestParam(required = false) WaitingStatus status) {
        access.booth(boothId);
        var b = booths.findByIdAndDeletedFalse(boothId).orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND,"B001","부스를 찾을 수 없습니다."));
        var rows = waitings.findByBoothIdAndStatusInOrderByRegisteredAtAsc(boothId,
                status == null ? List.of(WaitingStatus.values()) : List.of(status));
        var content = rows.stream().map(w -> {
            Map<String, Object> item = new LinkedHashMap<>(workflow.ticket(w));
            item.put("studentNumberMasked", PersonalDataMasker.studentNumber(w.getStudentNumber()));
            item.put("phoneNumberMasked", PersonalDataMasker.phoneNumber(w.getPhoneNumber()));
            item.put("elapsedMinutes", java.time.Duration.between(w.getRegisteredAt(), ApiClock.now()).toMinutes());
            item.put("smsStatus", "NOT_CONFIGURED");
            return item;
        }).toList();
        var today = ApiClock.now().toLocalDate().atStartOfDay();
        Map<String, Object> summary = Map.of("waiting", waitings.countByBoothIdAndStatus(boothId, WaitingStatus.WAITING),
                "called", waitings.countByBoothIdAndStatus(boothId, WaitingStatus.CALLED),
                "enteredToday", waitings.countByBoothIdAndStatusAndClosedAtGreaterThanEqual(boothId, WaitingStatus.ENTERED, today),
                "autoCanceledToday", waitings.countByBoothIdAndStatusAndClosedAtGreaterThanEqual(boothId, WaitingStatus.AUTO_CANCELED, today),
                "canceledToday", waitings.countByBoothIdAndStatusAndClosedAtGreaterThanEqual(boothId, WaitingStatus.CANCELED, today));
        return ApiResponse.ok(Map.of("summary", summary, "waitingTeamCount", summary.get("waiting"),
                "estimatedWaitMinutes", (long) summary.get("waiting") * b.getEstimateMinutesPerTeam(),
                "acceptingStatus", b.getAcceptingStatus(), "content", content, "serverTime", ApiClock.offset(ApiClock.now())));
    }

    @PostMapping("/waitings/{waitingId}/call")
    public ApiResponse<Map<String, Object>> call(@PathVariable String waitingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var w = workflow.find(waitingId); access.booth(w.getBooth().getId());
        workflow.autoCancel(waitingId);
        return ApiResponse.ok(workflow.call(waitingId, access.current(), key));
    }

    @PostMapping("/waitings/{waitingId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable String waitingId) {
        var w = workflow.find(waitingId); access.booth(w.getBooth().getId());
        return ApiResponse.ok(workflow.adminClose(waitingId, access.current(), WaitingStatus.CANCELED));
    }

    @PostMapping("/waitings/{waitingId}/enter")
    public ApiResponse<Map<String, Object>> enter(@PathVariable String waitingId) {
        var w = workflow.find(waitingId); access.booth(w.getBooth().getId());
        workflow.autoCancel(waitingId);
        return ApiResponse.ok(workflow.adminClose(waitingId, access.current(), WaitingStatus.ENTERED));
    }

    @PostMapping("/waitings/{waitingId}/no-show")
    public ApiResponse<Map<String, Object>> noShow(@PathVariable String waitingId) {
        var w = workflow.find(waitingId); access.booth(w.getBooth().getId());
        return ApiResponse.ok(workflow.adminClose(waitingId, access.current(), WaitingStatus.NO_SHOW));
    }

    @GetMapping("/waitings/{waitingId}/contact")
    @Transactional
    public ApiResponse<Map<String, Object>> contact(@PathVariable String waitingId) {
        var w = workflow.find(waitingId);
        var actor = access.booth(w.getBooth().getId());
        audit.save(AuditLog.builder().adminAccount(actor).action("WAITING_CONTACT_VIEW")
                .targetType("WAITING").targetId(waitingId).occurredAt(ApiClock.now()).build());
        if (w.getPhoneNumber().equals("00000000000"))
            throw new ContractException(HttpStatus.CONFLICT, "W010", "파기된 연락처입니다.");
        return ApiResponse.ok(Map.of("waitingId", waitingId, "phoneNumber", w.getPhoneNumber()));
    }
}
