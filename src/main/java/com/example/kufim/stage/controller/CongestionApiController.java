package com.example.kufim.stage.controller;

import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.common.util.Csv;
import com.example.kufim.security.AdminAccess;
import com.example.kufim.stage.entity.CongestionLevel;
import com.example.kufim.stage.entity.CongestionLog;
import com.example.kufim.stage.entity.StageZone;
import com.example.kufim.stage.repository.CongestionLogRepository;
import com.example.kufim.stage.repository.StageRepository;
import com.example.kufim.stage.repository.StageZoneRepository;
import com.example.kufim.waiting.service.WaitingRealtimePublisher;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 무대 구역과 혼잡도 입력·조회 (요구사항 STG-001~008, ADM-004, ADM-010). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CongestionApiController {
    static final int MAX_ACTIVE_ZONES = 10;
    static final int MAX_PEOPLE = 9999;

    private final AdminAccess access;
    private final StageRepository stages;
    private final StageZoneRepository zones;
    private final CongestionLogRepository logs;
    private final WaitingRealtimePublisher realtime;

    public record ZoneInput(String name, Integer displayOrder, Boolean active) {}
    public record CongestionInput(CongestionLevel level, Integer estimatedPeople) {}

    @GetMapping("/stages/{stageId}/congestion/history")
    @Operation(summary = "혼잡도 추이", description = "기본 최근 3시간. from/to는 시간대를 포함한 ISO 시각, 최대 24시간.", tags = "이용자 · 무대")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> history(@PathVariable long stageId, @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to, @RequestParam(required = false) Long zoneId) {
        stages.findById(stageId).orElseThrow(() -> missing("무대"));
        LocalDateTime end = to == null ? ApiClock.now() : local(to);
        LocalDateTime start = from == null ? end.minusHours(3) : local(from);
        if (!end.isAfter(start) || start.plusHours(24).isBefore(end)) throw invalid("조회 구간은 24시간 이내로 지정해 주세요.");
        var points = logs.history(stageId, zoneId, start, end).stream().map(l -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("zoneId", l.getZone().getId()); p.put("level", l.getLevel());
            p.put("estimatedPeople", l.getEstimatedPeople()); p.put("recordedAt", ApiClock.offset(l.getRecordedAt()));
            return p;
        }).toList();
        return ApiResponse.ok(Map.of("serverTime", ApiClock.offset(ApiClock.now()), "points", points));
    }

    @GetMapping("/admin/stages/{stageId}/zones")
    @Operation(summary = "무대 구역 목록", description = "비활성 구역과 마지막 입력자까지 포함합니다.", tags = "관리자 · 혼잡도")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> zoneList(@PathVariable long stageId) {
        access.superAdmin();
        stages.findById(stageId).orElseThrow(() -> missing("무대"));
        return ApiResponse.ok(Map.of("serverTime", ApiClock.offset(ApiClock.now()),
                "zones", zones.findByStage(stageId).stream().map(z -> zoneView(z, true)).toList()));
    }

    @PostMapping("/admin/stages/{stageId}/zones")
    @Operation(summary = "무대 구역 등록", description = "무대당 활성 구역은 최대 10개입니다.", tags = "관리자 · 혼잡도")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createZone(@PathVariable long stageId, @RequestBody ZoneInput input) {
        access.superAdmin();
        var stage = stages.findById(stageId).orElseThrow(() -> missing("무대"));
        String name = checkName(input.name(), true);
        if (zones.countByStageIdAndActiveTrue(stageId) >= MAX_ACTIVE_ZONES) throw zoneLimit();
        var zone = zones.save(StageZone.builder().stage(stage).name(name)
                .displayOrder(input.displayOrder() == null ? 0 : input.displayOrder()).build());
        return ResponseEntity.status(201).body(ApiResponse.ok(zoneView(zone, true)));
    }

    @PatchMapping("/admin/zones/{zoneId}")
    @Operation(summary = "무대 구역 수정", description = "이름·순서·활성 여부. 이력이 있는 구역은 삭제 대신 active=false로 숨깁니다.", tags = "관리자 · 혼잡도")
    @Transactional
    public ApiResponse<Map<String, Object>> editZone(@PathVariable long zoneId, @RequestBody ZoneInput input) {
        access.superAdmin();
        var zone = zones.lock(zoneId).orElseThrow(() -> missing("구역"));
        String name = checkName(input.name(), false);
        if (Boolean.TRUE.equals(input.active()) && !zone.isActive()
                && zones.countByStageIdAndActiveTrue(zone.getStage().getId()) >= MAX_ACTIVE_ZONES) throw zoneLimit();
        zone.edit(name, input.displayOrder(), input.active());
        return ApiResponse.ok(zoneView(zone, true));
    }

    @DeleteMapping("/admin/zones/{zoneId}")
    @Operation(summary = "무대 구역 삭제", description = "혼잡도 이력이 있으면 삭제할 수 없습니다(사후 분석 원자료 보존).", tags = "관리자 · 혼잡도")
    @Transactional
    public ResponseEntity<Void> deleteZone(@PathVariable long zoneId) {
        access.superAdmin();
        var zone = zones.lock(zoneId).orElseThrow(() -> missing("구역"));
        if (logs.existsByZoneId(zoneId))
            throw new ContractException(HttpStatus.CONFLICT, "Z002", "혼잡도 이력이 있는 구역은 삭제할 수 없습니다. 비활성으로 바꿔 주세요.");
        zones.delete(zone);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/zones/{zoneId}/congestion")
    @Operation(summary = "혼잡도 입력", description = "4단계와 선택 입력인 추정 인원(0~9999). 이력에 추가만 하고 저장 즉시 /topic/congestion으로 해당 구역만 보냅니다.", tags = "관리자 · 혼잡도")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> record(@PathVariable long zoneId, @RequestBody CongestionInput input) {
        var actor = access.superAdmin();
        if (input.level() == null) throw invalid("혼잡도 단계를 선택해 주세요.");
        if (input.estimatedPeople() != null && (input.estimatedPeople() < 0 || input.estimatedPeople() > MAX_PEOPLE))
            throw invalid("추정 인원은 0~9999명으로 입력해 주세요.");
        var zone = zones.lock(zoneId).orElseThrow(() -> missing("구역"));
        if (!zone.isActive()) throw new ContractException(HttpStatus.CONFLICT, "Z003", "비활성 구역에는 입력할 수 없습니다.");
        var log = logs.save(CongestionLog.builder().zone(zone).level(input.level()).estimatedPeople(input.estimatedPeople())
                .recordedBy(actor).recordedAt(ApiClock.now()).build());
        zone.recorded(log);
        var view = zoneView(zone, false);
        Map<String, Object> event = new LinkedHashMap<>(view);
        event.put("type", "CONGESTION_UPDATED");
        realtime.congestion(event);
        return ResponseEntity.status(201).body(ApiResponse.ok(zoneView(zone, true)));
    }

    @GetMapping("/admin/analytics/congestion.csv")
    @Operation(summary = "혼잡도 이력 CSV", description = "사후 분석용. UTF-8 BOM 포함, 기본 전체 기간.", tags = "관리자 · 혼잡도")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to) {
        access.superAdmin();
        LocalDateTime start = from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : local(from);
        LocalDateTime end = to == null ? ApiClock.now() : local(to);
        var csv = new Csv("recorded_at", "stage", "zone", "level", "estimated_people", "recorded_by");
        for (var l : logs.export(start, end))
            csv.row(l.getRecordedAt(), l.getZone().getStage().getName(), l.getZone().getName(), l.getLevel(),
                    l.getEstimatedPeople(), l.getRecordedBy() == null ? "" : l.getRecordedBy().getName());
        return csv.download("kufim-congestion.csv");
    }

    /** 공개 응답에는 입력자를 넣지 않습니다. */
    public static Map<String, Object> zoneView(StageZone z, boolean admin) {
        Map<String, Object> view = new LinkedHashMap<>();
        var latest = z.getLatestLog();
        view.put("zoneId", z.getId()); view.put("stageId", z.getStage().getId()); view.put("name", z.getName());
        view.put("displayOrder", z.getDisplayOrder());
        view.put("level", latest == null ? null : latest.getLevel());
        view.put("estimatedPeople", latest == null ? null : latest.getEstimatedPeople());
        view.put("updatedAt", latest == null ? null : ApiClock.offset(latest.getRecordedAt()));
        if (admin) {
            view.put("active", z.isActive());
            view.put("recordedBy", latest == null || latest.getRecordedBy() == null ? null : latest.getRecordedBy().getName());
        }
        return view;
    }
    private String checkName(String name, boolean required) {
        if (name == null) { if (required) throw invalid("구역 이름을 입력해 주세요."); return null; }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) throw invalid("구역 이름은 1~100자로 입력해 주세요.");
        return trimmed;
    }
    private LocalDateTime local(OffsetDateTime time) { return time.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime(); }
    private ContractException zoneLimit() {
        return new ContractException(HttpStatus.CONFLICT, "Z001", "무대당 활성 구역은 최대 " + MAX_ACTIVE_ZONES + "개입니다.");
    }
    private ContractException invalid(String message) { return new ContractException(HttpStatus.BAD_REQUEST, "C001", message); }
    private ContractException missing(String what) {
        return new ContractException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", what + "을(를) 찾을 수 없습니다.");
    }
}
