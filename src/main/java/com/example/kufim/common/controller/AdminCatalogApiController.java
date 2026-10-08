package com.example.kufim.common.controller;

import com.example.kufim.admin.repository.AdminBoothRepository;
import com.example.kufim.booth.entity.*;
import com.example.kufim.booth.repository.BoothRepository;
import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.facility.entity.*;
import com.example.kufim.facility.repository.FacilityRepository;
import com.example.kufim.festival.repository.FestivalRepository;
import com.example.kufim.map.entity.*;
import com.example.kufim.map.repository.MapFloorplanRepository;
import com.example.kufim.map.repository.MapPinRepository;
import com.example.kufim.notice.entity.*;
import com.example.kufim.notice.repository.NoticeRepository;
import com.example.kufim.notification.entity.Notification;
import com.example.kufim.notification.entity.NotificationType;
import com.example.kufim.notification.repository.NotificationRepository;
import com.example.kufim.performance.entity.*;
import com.example.kufim.performance.repository.PerformanceRepository;
import com.example.kufim.security.AdminAccess;
import com.example.kufim.stage.entity.*;
import com.example.kufim.stage.repository.StageRepository;
import com.example.kufim.waiting.entity.WaitingStatus;
import com.example.kufim.waiting.repository.WaitingRepository;
import com.example.kufim.waiting.service.WaitingRealtimePublisher;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCatalogApiController {
    private final AdminAccess access;
    private final AdminBoothRepository assignments;
    private final FestivalRepository festivals;
    private final StageRepository stages;
    private final PerformanceRepository performances;
    private final BoothRepository booths;
    private final FacilityRepository facilities;
    private final NoticeRepository notices;
    private final NotificationRepository notifications;
    private final MapFloorplanRepository floorplans;
    private final MapPinRepository pins;
    private final WaitingRepository waitings;
    private final WaitingRealtimePublisher realtime;
    private final ObjectMapper json;

    public record PinInput(@NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal xPercent,
                           @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal yPercent) {}
    public record PerformanceInput(Long festivalId, Long stageId, String title, PerformanceType type,
            String description, String additionalDescription, String castMembers, String setlist,
            OffsetDateTime startAt, OffsetDateTime endAt, Integer displayOrder) {}
    public record BoothInput(Long festivalId, String name, OrganizerType organizerType, BoothPeriod period,
            String place, String description, String operatingHours, Boolean waitingEnabled, @Valid PinInput pin) {}
    public record FacilityInput(Long festivalId, FacilityType type, String name, String description,
            String place, String operatingHours, @Valid PinInput pin) {}
    public record NoticeInput(String title, String content, NoticeCategory category) {}
    public record StageInput(Long festivalId, String name, String description, Integer displayOrder) {}
    public record EntryInput(@NotNull EntryStatus entryStatus) {}

    @GetMapping("/performances")
    @Operation(summary = "무대 프로그램 목록", tags = "관리자 · 무대 프로그램")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> performances(@RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long stageId) {
        access.superAdmin();
        return ApiResponse.ok(performances.findAllByDeletedFalseOrderByStartAtAsc().stream()
                .filter(p -> date == null || p.getStartAt().toLocalDate().equals(date))
                .filter(p -> stageId == null || p.getStage().getId().equals(stageId))
                .map(this::performanceView).toList());
    }
    @PostMapping("/performances")
    @Operation(summary = "무대 프로그램 등록", description = "관리자 화면의 무대 프로그램 등록. 기존 무대의 stageId와 축제 festivalId를 지정합니다.", tags = "관리자 · 무대 프로그램")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPerformance(@RequestBody PerformanceInput input) {
        access.superAdmin();
        if (input.festivalId() == null || input.stageId() == null || blank(input.title()) || input.title().length() > 15
                || input.type() == null || input.startAt() == null || input.endAt() == null) throw invalid();
        var festival = festivals.findById(input.festivalId()).orElseThrow(() -> missing("축제"));
        var stage = stages.findById(input.stageId()).orElseThrow(() -> missing("무대"));
        if (!stage.getFestival().getId().equals(festival.getId())) throw invalid();
        checkTime(input.startAt(), input.endAt());
        var p = performances.save(Performance.builder().festival(festival).stage(stage).title(input.title())
                .type(input.type()).description(input.description()).additionalDescription(input.additionalDescription())
                .castMembers(input.castMembers()).setlist(input.setlist()).startAt(local(input.startAt())).endAt(local(input.endAt()))
                .displayOrder(input.displayOrder() == null ? 0 : input.displayOrder()).build());
        return ResponseEntity.status(201).body(ApiResponse.ok(performanceView(p)));
    }
    @PatchMapping("/performances/{id}")
    @Operation(summary = "무대 프로그램 수정", tags = "관리자 · 무대 프로그램")
    @Transactional
    public ApiResponse<Map<String, Object>> editPerformance(@PathVariable long id, @RequestBody PerformanceInput input) {
        access.superAdmin();
        var p = performances.findByIdAndDeletedFalse(id).orElseThrow(() -> missing("공연"));
        if (input.festivalId() != null && !input.festivalId().equals(p.getFestival().getId())) throw invalid();
        var stage = input.stageId() == null ? null : stages.findById(input.stageId()).orElseThrow(() -> missing("무대"));
        if (stage != null && !stage.getFestival().getId().equals(p.getFestival().getId())) throw invalid();
        if (input.title() != null && (blank(input.title()) || input.title().length() > 15)) throw invalid();
        var start = input.startAt() == null ? p.getStartAt() : local(input.startAt());
        var end = input.endAt() == null ? p.getEndAt() : local(input.endAt());
        if (!end.isAfter(start)) throw invalid();
        p.edit(stage, input.title(), input.type(), input.description(), input.additionalDescription(), input.castMembers(),
                input.setlist(), start, end, input.displayOrder());
        return ApiResponse.ok(performanceView(p));
    }
    @DeleteMapping("/performances/{id}")
    @Operation(summary = "무대 프로그램 삭제", tags = "관리자 · 무대 프로그램")
    @Transactional
    public ResponseEntity<Void> deletePerformance(@PathVariable long id) {
        access.superAdmin(); performances.findByIdAndDeletedFalse(id).orElseThrow(() -> missing("공연")).delete();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/booths")
    @Operation(summary = "부스 프로그램 목록", tags = "관리자 · 부스 프로그램")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> booths() {
        var actor = access.current();
        var allowed = assignments.findByAdminAccountId(actor.getId()).stream().map(a -> a.getBooth().getId()).collect(java.util.stream.Collectors.toSet());
        return ApiResponse.ok(booths.findAllByDeletedFalseOrderByIdAsc().stream()
                .filter(b -> actor.getRole() == com.example.kufim.admin.entity.AdminRole.SUPER_ADMIN || allowed.contains(b.getId()))
                .map(this::boothView).toList());
    }
    @PostMapping("/booths")
    @Operation(summary = "부스 프로그램 등록", description = "관리자 화면의 부스 프로그램 등록. 시설 탭의 항목은 /admin/facilities로 등록합니다.", tags = "관리자 · 부스 프로그램")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBooth(@RequestBody @Valid BoothInput input) {
        access.superAdmin();
        if (input.festivalId() == null || blank(input.name()) || blank(input.place()) || input.organizerType() == null || input.period() == null) throw invalid();
        var festival = festivals.findById(input.festivalId()).orElseThrow(() -> missing("축제"));
        var b = booths.save(Booth.builder().festival(festival).name(input.name()).organizerType(input.organizerType())
                .period(input.period()).place(input.place()).description(input.description()).operatingHours(input.operatingHours())
                .waitingEnabled(Boolean.TRUE.equals(input.waitingEnabled())).boothCode(code()).qrIssuedAt(ApiClock.now()).build());
        if (input.pin() != null) setPin(MapTargetType.BOOTH, b.getId(), b.getFestival().getId(), input.pin());
        return ResponseEntity.status(201).body(ApiResponse.ok(boothView(b)));
    }
    @PatchMapping("/booths/{id}")
    @Operation(summary = "부스 프로그램 수정", description = "pin 생략 시 기존 위치 유지, pin:null이면 위치 해제", tags = "관리자 · 부스 프로그램")
    @Transactional
    public ApiResponse<Map<String, Object>> editBooth(@PathVariable long id, @RequestBody JsonNode body) {
        access.superAdmin();
        BoothInput input = json.treeToValue(body, BoothInput.class);
        var b = booths.findByIdAndDeletedFalse(id).orElseThrow(() -> missing("부스"));
        if (input.festivalId() != null && !input.festivalId().equals(b.getFestival().getId())) throw invalid();
        if (input.name() != null && blank(input.name()) || input.place() != null && blank(input.place())) throw invalid();
        b.edit(input.name(), input.organizerType(), input.period(), input.place(), input.description(), input.operatingHours(), input.waitingEnabled());
        if (body.has("pin") && body.get("pin").isNull()) pins.findByTargetTypeAndTargetId(MapTargetType.BOOTH, id).ifPresent(pins::delete);
        else if (input.pin() != null) setPin(MapTargetType.BOOTH, id, b.getFestival().getId(), input.pin());
        return ApiResponse.ok(boothView(b));
    }
    @DeleteMapping("/booths/{id}")
    @Operation(summary = "부스 프로그램 삭제", tags = "관리자 · 부스 프로그램")
    @Transactional
    public ResponseEntity<Void> deleteBooth(@PathVariable long id) {
        access.superAdmin();
        var b = booths.findByIdAndDeletedFalse(id).orElseThrow(() -> missing("부스"));
        if (waitings.countByBoothIdAndStatus(id, WaitingStatus.WAITING) + waitings.countByBoothIdAndStatus(id, WaitingStatus.CALLED) > 0)
            throw new ContractException(HttpStatus.CONFLICT, "W001", "활성 대기표가 있어 삭제할 수 없습니다.");
        b.delete(); pins.findByTargetTypeAndTargetId(MapTargetType.BOOTH, id).ifPresent(pins::delete);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/facilities")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> facilities() {
        access.superAdmin();
        return ApiResponse.ok(facilities.findAllByDeletedFalseOrderByIdAsc().stream().map(this::facilityView).toList());
    }
    @PostMapping("/facilities")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createFacility(@RequestBody @Valid FacilityInput input) {
        access.superAdmin();
        if (input.festivalId() == null || input.type() == null || blank(input.name()) || blank(input.place())) throw invalid();
        var festival = festivals.findById(input.festivalId()).orElseThrow(() -> missing("축제"));
        var f = facilities.save(Facility.builder().festival(festival).type(input.type()).name(input.name())
                .description(input.description()).place(input.place()).operatingHours(input.operatingHours()).build());
        if (input.pin() != null) setPin(MapTargetType.FACILITY, f.getId(), f.getFestival().getId(), input.pin());
        return ResponseEntity.status(201).body(ApiResponse.ok(facilityView(f)));
    }
    @PatchMapping("/facilities/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> editFacility(@PathVariable long id, @RequestBody JsonNode body) {
        access.superAdmin();
        FacilityInput input = json.treeToValue(body, FacilityInput.class);
        var f = facilities.findByIdAndDeletedFalse(id).orElseThrow(() -> missing("시설"));
        if (input.festivalId() != null && !input.festivalId().equals(f.getFestival().getId())) throw invalid();
        f.edit(input.type(), input.name(), input.description(), input.place(), input.operatingHours());
        if (body.has("pin") && body.get("pin").isNull()) pins.findByTargetTypeAndTargetId(MapTargetType.FACILITY, id).ifPresent(pins::delete);
        else if (input.pin() != null) setPin(MapTargetType.FACILITY, id, f.getFestival().getId(), input.pin());
        return ApiResponse.ok(facilityView(f));
    }
    @DeleteMapping("/facilities/{id}")
    @Transactional
    public ResponseEntity<Void> deleteFacility(@PathVariable long id) {
        access.superAdmin(); facilities.findByIdAndDeletedFalse(id).orElseThrow(() -> missing("시설")).delete();
        pins.findByTargetTypeAndTargetId(MapTargetType.FACILITY, id).ifPresent(pins::delete);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notices")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> notices() {
        access.superAdmin();
        return ApiResponse.ok(notices.findAllByDeletedFalseAndPublishedAtLessThanEqualOrderByPublishedAtDesc(ApiClock.now())
                .stream().map(this::noticeView).toList());
    }
    @PostMapping("/notices")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createNotice(@RequestBody NoticeInput input) {
        var actor = access.superAdmin();
        if (blank(input.title()) || input.title().length() > 25 || blank(input.content()) || input.category() == null) throw invalid();
        var festival = festivals.findAll().stream().max(Comparator.comparingLong(f -> f.getId())).orElseThrow(() -> missing("축제"));
        var n = notices.save(Notice.builder().festival(festival).title(input.title()).content(input.content())
                .category(input.category()).publishedAt(ApiClock.now()).createdBy(actor).build());
        notifications.save(Notification.builder().festival(festival).type(NotificationType.NOTICE).notice(n)
                .title(n.getTitle()).body(n.getContent()).build());
        realtime.notice("NOTICE_PUBLISHED", n.getId());
        return ResponseEntity.status(201).body(ApiResponse.ok(noticeView(n)));
    }
    @PatchMapping("/notices/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> editNotice(@PathVariable long id, @RequestBody NoticeInput input) {
        access.superAdmin();
        var n = notices.findById(id).filter(row -> !row.isDeleted()).orElseThrow(() -> missing("공지"));
        if (input.title() != null && (blank(input.title()) || input.title().length() > 25)) throw invalid();
        n.edit(input.category(), input.title(), input.content());
        notifications.findByNoticeId(id).ifPresent(notification -> notification.updateContent(n.getTitle(), n.getContent()));
        realtime.notice("NOTICE_UPDATED", n.getId());
        return ApiResponse.ok(noticeView(n));
    }
    @DeleteMapping("/notices/{id}")
    @Transactional
    public ResponseEntity<Void> deleteNotice(@PathVariable long id) {
        access.superAdmin(); notices.findById(id).filter(row -> !row.isDeleted()).orElseThrow(() -> missing("공지")).delete();
        realtime.notice("NOTICE_REMOVED", id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stages")
    @Operation(summary = "무대 목록", tags = "관리자 · 무대")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> stages() {
        access.superAdmin(); return ApiResponse.ok(stages.findAll().stream().map(this::stageView).toList());
    }
    @PostMapping("/stages")
    @Operation(summary = "무대 등록", description = "공연 장소인 무대 자체를 등록합니다. 무대 프로그램 등록은 /admin/performances를 사용합니다.", tags = "관리자 · 무대")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createStage(@RequestBody StageInput input) {
        access.superAdmin();
        if (input.festivalId() == null || blank(input.name())) throw invalid();
        var festival = festivals.findById(input.festivalId()).orElseThrow(() -> missing("축제"));
        var stage = stages.save(Stage.builder().festival(festival).name(input.name()).description(input.description())
                .displayOrder(input.displayOrder() == null ? 0 : input.displayOrder()).build());
        return ResponseEntity.status(201).body(ApiResponse.ok(stageView(stage)));
    }
    @PatchMapping("/stages/{id}")
    @Operation(summary = "무대 수정", tags = "관리자 · 무대")
    @Transactional
    public ApiResponse<Map<String, Object>> editStage(@PathVariable long id, @RequestBody StageInput input) {
        access.superAdmin(); var s = stages.findById(id).orElseThrow(() -> missing("무대"));
        if (input.festivalId() != null && !input.festivalId().equals(s.getFestival().getId())) throw invalid();
        s.edit(input.name(), input.description(), input.displayOrder()); return ApiResponse.ok(stageView(s));
    }
    @PatchMapping("/stages/{id}/entry-status")
    @Operation(summary = "무대 입장 상태 변경", tags = "관리자 · 무대")
    @Transactional
    public ApiResponse<Map<String, Object>> entry(@PathVariable long id, @RequestBody @Valid EntryInput input) {
        access.superAdmin(); var s = stages.findById(id).orElseThrow(() -> missing("무대"));
        s.entry(input.entryStatus()); return ApiResponse.ok(stageView(s));
    }
    @DeleteMapping("/stages/{id}")
    @Operation(summary = "무대 삭제", tags = "관리자 · 무대")
    @Transactional
    public ResponseEntity<Void> deleteStage(@PathVariable long id) {
        access.superAdmin();
        var stage = stages.findById(id).orElseThrow(() -> missing("무대"));
        if (performances.findAll().stream().anyMatch(p -> p.getStage().getId().equals(id)))
            throw new ContractException(HttpStatus.CONFLICT,"C001","연결된 공연이 있어 무대를 삭제할 수 없습니다.");
        stages.delete(stage); return ResponseEntity.noContent().build();
    }

    private void setPin(MapTargetType type, long targetId, long festivalId, PinInput input) {
        if (input.xPercent() == null || input.yPercent() == null || input.xPercent().compareTo(BigDecimal.ZERO) < 0
                || input.xPercent().compareTo(BigDecimal.valueOf(100)) > 0 || input.yPercent().compareTo(BigDecimal.ZERO) < 0
                || input.yPercent().compareTo(BigDecimal.valueOf(100)) > 0) throw invalid();
        var current = pins.findByTargetTypeAndTargetId(type, targetId);
        if (current.isPresent()) current.get().move(input.xPercent(), input.yPercent());
        else {
            var floor = floorplans.findTopByFestivalIdOrderByIdDesc(festivalId)
                    .orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND,"M003","등록된 도면이 없습니다."));
            pins.save(MapPin.builder().floorplan(floor).targetType(type).targetId(targetId)
                    .xPercent(input.xPercent()).yPercent(input.yPercent()).build());
        }
    }
    private Map<String, Object> performanceView(Performance p) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("performanceId", p.getId()); view.put("festivalId", p.getFestival().getId()); view.put("stageId", p.getStage().getId());
        view.put("title", p.getTitle()); view.put("type", p.getType()); view.put("description", p.getDescription());
        view.put("additionalDescription", p.getAdditionalDescription()); view.put("castMembers", p.getCastMembers()); view.put("setlist", p.getSetlist());
        view.put("startAt", ApiClock.offset(p.getStartAt())); view.put("endAt", ApiClock.offset(p.getEndAt())); view.put("displayOrder", p.getDisplayOrder());
        return view;
    }
    private Map<String, Object> boothView(Booth b) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("boothId", b.getId()); view.put("festivalId", b.getFestival().getId()); view.put("name", b.getName());
        view.put("organizerType", b.getOrganizerType()); view.put("period", b.getPeriod()); view.put("place", b.getPlace());
        view.put("description", b.getDescription()); view.put("operatingHours", b.getOperatingHours());
        view.put("waitingEnabled", b.isWaitingEnabled()); view.put("acceptingStatus", b.getAcceptingStatus());
        view.put("pin", pins.findByTargetTypeAndTargetId(MapTargetType.BOOTH, b.getId())
                .map(p -> Map.of("xPercent", p.getXPercent(), "yPercent", p.getYPercent())).orElse(null));
        return view;
    }
    private Map<String, Object> facilityView(Facility f) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("facilityId", f.getId()); view.put("festivalId", f.getFestival().getId()); view.put("type", f.getType());
        view.put("name", f.getName()); view.put("description", f.getDescription()); view.put("place", f.getPlace());
        view.put("operatingHours", f.getOperatingHours());
        view.put("pin", pins.findByTargetTypeAndTargetId(MapTargetType.FACILITY, f.getId())
                .map(p -> Map.of("xPercent", p.getXPercent(), "yPercent", p.getYPercent())).orElse(null));
        return view;
    }
    private Map<String, Object> noticeView(Notice n) {
        return Map.of("noticeId", n.getId(), "title", n.getTitle(), "content", n.getContent(),
                "category", n.getCategory(), "publishedAt", ApiClock.offset(n.getPublishedAt()));
    }
    private Map<String, Object> stageView(Stage s) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("stageId", s.getId()); view.put("festivalId", s.getFestival().getId()); view.put("name", s.getName());
        view.put("description", s.getDescription()); view.put("entryStatus", s.getEntryStatus()); view.put("displayOrder", s.getDisplayOrder());
        return view;
    }
    private LocalDateTime local(OffsetDateTime time) { return time.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime(); }
    private void checkTime(OffsetDateTime start, OffsetDateTime end) { if (!end.isAfter(start)) throw invalid(); }
    private String code() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ContractException invalid() { return new ContractException(HttpStatus.BAD_REQUEST,"C001","입력값을 확인해 주세요."); }
    private ContractException missing(String name) { return new ContractException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND",name + "을(를) 찾을 수 없습니다."); }
}
