package com.example.kufim.common.controller;

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
import com.example.kufim.notification.repository.NotificationReadRepository;
import com.example.kufim.notification.repository.NotificationRepository;
import com.example.kufim.performance.entity.*;
import com.example.kufim.performance.repository.PerformanceRepository;
import com.example.kufim.stage.entity.Stage;
import com.example.kufim.stage.repository.StageRepository;
import com.example.kufim.waiting.entity.WaitingStatus;
import com.example.kufim.waiting.repository.WaitingRepository;
import com.example.kufim.waiting.service.ClientToken;
import com.example.kufim.waiting.service.WaitingWorkflowService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicCatalogApiController {
    private final FestivalRepository festivals;
    private final PerformanceRepository performances;
    private final BoothRepository booths;
    private final FacilityRepository facilities;
    private final StageRepository stages;
    private final MapFloorplanRepository floorplans;
    private final MapPinRepository pins;
    private final NoticeRepository notices;
    private final NotificationRepository notifications;
    private final NotificationReadRepository reads;
    private final WaitingRepository waitings;
    private final WaitingWorkflowService waitingWorkflow;

    @GetMapping("/festival")
    public ApiResponse<Map<String, Object>> festival() {
        var f = festivals.findAll().stream().max(Comparator.comparingLong(x -> x.getId())).orElseThrow(() -> missing("축제"));
        var today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("operatingBoothCount", booths.findAllByDeletedFalseOrderByIdAsc().stream()
                .filter(b -> b.getFestival().getId().equals(f.getId()) && b.getAcceptingStatus() != AcceptingStatus.CLOSED)
                .filter(b -> !ApiClock.now().isBefore(f.getStartAt()) && !ApiClock.now().isAfter(f.getEndAt())).count());
        summary.put("todayPerformanceCount", performances.findAllByDeletedFalseOrderByStartAtAsc().stream()
                .filter(p -> p.getFestival().getId().equals(f.getId()) && p.getStartAt().toLocalDate().equals(today)).count());
        summary.put("stageEntrySummary", stages.findAll().stream().filter(s -> s.getFestival().getId().equals(f.getId()))
                .map(s -> Map.of("stageId", s.getId(), "entryStatus", s.getEntryStatus())).toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("festivalId", f.getId()); data.put("name", f.getName()); data.put("location", f.getLocation());
        data.put("startAt", ApiClock.offset(f.getStartAt())); data.put("endAt", ApiClock.offset(f.getEndAt()));
        data.put("status", f.getStatus()); data.put("serverTime", ApiClock.offset(ApiClock.now())); data.put("summary", summary);
        return ApiResponse.ok(data);
    }

    @GetMapping("/performances")
    public ApiResponse<List<Map<String, Object>>> performances(@RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long stageId, @RequestParam(required = false) PerformanceType type) {
        return ApiResponse.ok(performances.findAllByDeletedFalseOrderByStartAtAsc().stream()
                .filter(p -> date == null || p.getStartAt().toLocalDate().equals(date))
                .filter(p -> stageId == null || p.getStage().getId().equals(stageId))
                .filter(p -> type == null || p.getType() == type).map(p -> performanceView(p, false)).toList());
    }

    @GetMapping("/performances/now")
    public ApiResponse<Map<String, Object>> now(@RequestParam(required = false) Long stageId) {
        LocalDateTime now = ApiClock.now();
        var all = performances.findAllByDeletedFalseOrderByStartAtAsc();
        var stageRows = stages.findAll().stream().filter(s -> stageId == null || s.getId().equals(stageId)).map(s -> {
            var forStage = all.stream().filter(p -> p.getStage().getId().equals(s.getId())).toList();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stageId", s.getId());
            row.put("current", forStage.stream().filter(p -> !p.getStartAt().isAfter(now) && p.getEndAt().isAfter(now))
                    .findFirst().map(p -> performanceView(p, false)).orElse(null));
            row.put("next", forStage.stream().filter(p -> p.getStartAt().isAfter(now)).findFirst()
                    .map(p -> performanceView(p, false)).orElse(null));
            return row;
        }).toList();
        return ApiResponse.ok(Map.of("serverTime", ApiClock.offset(now), "stages", stageRows));
    }

    @GetMapping("/performances/{id}")
    public ApiResponse<Map<String, Object>> performance(@PathVariable long id) {
        return ApiResponse.ok(performanceView(performances.findByIdAndDeletedFalse(id).orElseThrow(() -> missing("공연")), true));
    }

    @GetMapping("/booths")
    public ApiResponse<List<Map<String, Object>>> booths(@RequestParam(required = false) BoothPeriod period,
            @RequestParam(required = false) OrganizerType organizerType, @RequestParam(defaultValue = "false") boolean waitingOnly,
            @RequestParam(required = false) String sort, @RequestParam(required = false) Integer limit) {
        if (limit != null && (limit < 1 || limit > 100)) throw invalid();
        var source = booths.findAllByDeletedFalseOrderByIdAsc().stream()
                .filter(b -> period == null || b.getPeriod() == period)
                .filter(b -> organizerType == null || b.getOrganizerType() == organizerType)
                .filter(b -> !waitingOnly || b.isWaitingEnabled())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (sort != null) {
            if (!sort.equals("popular")) throw invalid();
            source.removeIf(b -> b.getAcceptingStatus() == AcceptingStatus.CLOSED);
            source.sort(Comparator.<Booth>comparingLong(b -> waitings.countByBoothIdAndStatus(b.getId(), WaitingStatus.WAITING))
                    .reversed().thenComparingLong(Booth::getId));
        }
        return ApiResponse.ok(source.stream().limit(limit == null ? Long.MAX_VALUE : limit).map(this::boothView).toList());
    }

    @GetMapping("/booths/{id}")
    public ApiResponse<Map<String, Object>> booth(@PathVariable long id) {
        var b = booths.findByIdAndDeletedFalse(id).orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND,"B001","부스를 찾을 수 없습니다."));
        var result = boothView(b); result.put("description", b.getDescription());
        return ApiResponse.ok(result);
    }

    @GetMapping("/booths/code/{code}")
    public ApiResponse<Map<String, Object>> boothCode(@PathVariable String code,
            @RequestHeader(value = "X-Client-Token", required = false) String token) {
        var b = booths.findByBoothCodeAndDeletedFalse(code).orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND,"B002","유효하지 않은 QR 코드입니다."));
        var result = boothView(b);
        result.put("myActiveWaitingId", waitingWorkflow.status(b.getId(), token).get("myActiveWaitingId"));
        return ApiResponse.ok(result);
    }

    @GetMapping("/facilities")
    public ApiResponse<List<Map<String, Object>>> facilities(@RequestParam(required = false) FacilityType type) {
        return ApiResponse.ok(facilities.findAllByDeletedFalseOrderByIdAsc().stream()
                .filter(f -> type == null || f.getType() == type).map(this::facilityView).toList());
    }

    @GetMapping("/facilities/{id}")
    public ApiResponse<Map<String, Object>> facility(@PathVariable long id) {
        return ApiResponse.ok(facilityView(facilities.findByIdAndDeletedFalse(id).orElseThrow(() -> missing("시설"))));
    }

    @GetMapping("/stages")
    public ApiResponse<List<Map<String, Object>>> stages() {
        return ApiResponse.ok(stages.findAll().stream().sorted(Comparator.comparingInt(Stage::getDisplayOrder))
                .map(s -> Map.<String, Object>of("stageId", s.getId(), "name", s.getName(), "entryStatus", s.getEntryStatus(),
                        "displayOrder", s.getDisplayOrder())).toList());
    }

    @GetMapping("/map")
    public ApiResponse<Map<String, Object>> map() {
        var f = floorplans.findAll().stream().max(Comparator.comparingLong(MapFloorplan::getId))
                .orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND,"M003","등록된 도면이 없습니다."));
        return ApiResponse.ok(Map.of("floorplan", Map.of("imageUrl", f.getImageUrl(), "width", f.getWidth(), "height", f.getHeight())));
    }

    @GetMapping("/map/pins")
    public ApiResponse<List<Map<String, Object>>> pins(@RequestParam(required = false) MapTargetType targetType,
            @RequestParam(required = false) BoothPeriod period, @RequestParam(required = false) OrganizerType organizerType,
            @RequestParam(required = false) FacilityType facilityType) {
        var result = new ArrayList<Map<String, Object>>();
        var latestFloorplan = floorplans.findAll().stream().map(MapFloorplan::getId).max(Long::compareTo).orElse(null);
        for (var pin : pins.findAll()) {
            if (latestFloorplan == null || !pin.getFloorplan().getId().equals(latestFloorplan)) continue;
            if (targetType != null && pin.getTargetType() != targetType) continue;
            if (pin.getTargetType() == MapTargetType.BOOTH) {
                var b = booths.findByIdAndDeletedFalse(pin.getTargetId());
                if (b.isEmpty() || period != null && b.get().getPeriod() != period || organizerType != null && b.get().getOrganizerType() != organizerType) continue;
                result.add(pinView(pin, b.get().getName()));
            } else if (pin.getTargetType() == MapTargetType.FACILITY) {
                var f = facilities.findByIdAndDeletedFalse(pin.getTargetId());
                if (f.isEmpty() || facilityType != null && f.get().getType() != facilityType) continue;
                result.add(pinView(pin, f.get().getName()));
            } else {
                var s = stages.findById(pin.getTargetId());
                if (s.isPresent()) result.add(pinView(pin, s.get().getName()));
            }
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/notices")
    public ApiResponse<List<Map<String, Object>>> notices(@RequestParam(required = false) NoticeCategory category,
            @RequestParam(defaultValue = "false") boolean urgentOnly,
            @RequestHeader(value = "X-Client-Token", required = false) String rawToken) {
        String token = rawToken == null ? null : ClientToken.require(rawToken);
        return ApiResponse.ok(notices.findAllByDeletedFalseAndPublishedAtLessThanEqualOrderByPublishedAtDesc(ApiClock.now()).stream()
                .filter(n -> category == null || n.getCategory() == category)
                .filter(n -> !urgentOnly || n.getCategory() == NoticeCategory.URGENT)
                .map(n -> noticeView(n, token)).toList());
    }

    @GetMapping("/notices/{id}")
    public ApiResponse<Map<String, Object>> notice(@PathVariable long id,
            @RequestHeader(value = "X-Client-Token", required = false) String rawToken) {
        String token = rawToken == null ? null : ClientToken.require(rawToken);
        return ApiResponse.ok(noticeView(notices.findByIdAndDeletedFalseAndPublishedAtLessThanEqual(id, ApiClock.now())
                .orElseThrow(() -> missing("공지")), token));
    }

    private Map<String, Object> performanceView(Performance p, boolean detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("performanceId", p.getId()); result.put("title", p.getTitle()); result.put("type", p.getType());
        result.put("stageId", p.getStage().getId()); result.put("stageName", p.getStage().getName()); result.put("place", p.getStage().getName());
        result.put("startAt", ApiClock.offset(p.getStartAt())); result.put("endAt", ApiClock.offset(p.getEndAt()));
        var now = ApiClock.now(); result.put("status", p.getEndAt().isBefore(now) ? "PAST" : p.getStartAt().isAfter(now) ? "NEXT" : "NOW");
        if (detail) {
            result.put("description", p.getDescription()); result.put("additionalDescription", p.getAdditionalDescription());
            result.put("castMembers", p.getCastMembers()); result.put("setlist", p.getSetlist());
        }
        return result;
    }
    private Map<String, Object> boothView(Booth b) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("boothId", b.getId()); result.put("name", b.getName()); result.put("organizerType", b.getOrganizerType());
        result.put("period", b.getPeriod()); result.put("place", b.getPlace()); result.put("operatingHours", b.getOperatingHours());
        result.put("waitingEnabled", b.isWaitingEnabled()); result.put("waitingStatus", b.isWaitingEnabled() ? b.getAcceptingStatus().name() : "DISABLED");
        result.put("pin", pins.findByTargetTypeAndTargetId(MapTargetType.BOOTH, b.getId()).map(p -> Map.of("xPercent", p.getXPercent(), "yPercent", p.getYPercent())).orElse(null));
        return result;
    }
    private Map<String, Object> facilityView(Facility f) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("facilityId", f.getId()); result.put("type", f.getType()); result.put("name", f.getName());
        result.put("description", f.getDescription()); result.put("place", f.getPlace()); result.put("operatingHours", f.getOperatingHours());
        result.put("pin", pins.findByTargetTypeAndTargetId(MapTargetType.FACILITY, f.getId()).map(p -> Map.of("xPercent", p.getXPercent(), "yPercent", p.getYPercent())).orElse(null));
        return result;
    }
    private Map<String, Object> pinView(MapPin p, String name) {
        return Map.of("pinId", p.getId(), "targetType", p.getTargetType(), "targetId", p.getTargetId(),
                "name", name, "xPercent", p.getXPercent(), "yPercent", p.getYPercent());
    }
    private Map<String, Object> noticeView(Notice n, String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("noticeId", n.getId()); result.put("title", n.getTitle()); result.put("content", n.getContent());
        result.put("category", n.getCategory()); result.put("publishedAt", ApiClock.offset(n.getPublishedAt()));
        result.put("unread", token == null ? null : notifications.findAll().stream().filter(a -> a.getNotice() != null && a.getNotice().getId().equals(n.getId()))
                .findFirst().map(a -> reads.findByNotificationIdAndClientToken(a.getId(), token).isEmpty()).orElse(false));
        return result;
    }
    private ContractException missing(String what) { return new ContractException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND",what + "을(를) 찾을 수 없습니다."); }
    private ContractException invalid() { return new ContractException(HttpStatus.BAD_REQUEST,"C001","입력값을 확인해 주세요."); }
}
