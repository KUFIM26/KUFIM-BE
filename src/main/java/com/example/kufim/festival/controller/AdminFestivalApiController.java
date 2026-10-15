package com.example.kufim.festival.controller;

import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.festival.entity.Festival;
import com.example.kufim.festival.entity.FestivalStatus;
import com.example.kufim.festival.repository.FestivalRepository;
import com.example.kufim.security.AdminAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/festivals")
@RequiredArgsConstructor
public class AdminFestivalApiController {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final FestivalRepository festivals;
    private final AdminAccess access;

    public record FestivalInput(
            @NotBlank @Size(max = 100) String name,
            String description,
            @Size(max = 200) String location,
            @NotNull @Schema(example = "2026-10-08T09:00:00+09:00") OffsetDateTime startAt,
            @NotNull @Schema(example = "2026-10-09T23:00:00+09:00") OffsetDateTime endAt,
            FestivalStatus status) {}

    public record FestivalPatch(@Size(max = 100) String name, String description, @Size(max = 200) String location,
            OffsetDateTime startAt, OffsetDateTime endAt, FestivalStatus status) {}

    public record FestivalView(long festivalId, String name, String description, String location,
            OffsetDateTime startAt, OffsetDateTime endAt, FestivalStatus status) {
        static FestivalView from(Festival festival) {
            return new FestivalView(festival.getId(), festival.getName(), festival.getDescription(),
                    festival.getLocation(), ApiClock.offset(festival.getStartAt()),
                    ApiClock.offset(festival.getEndAt()), festival.getStatus());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "축제 목록 조회", description = "부스·무대 등록에 사용할 festivalId를 확인합니다.", tags = "관리자 · 축제")
    public ApiResponse<List<FestivalView>> list() {
        access.superAdmin();
        return ApiResponse.ok(festivals.findAll().stream()
                .sorted(Comparator.comparingLong(Festival::getId).reversed())
                .map(FestivalView::from).toList());
    }

    @PostMapping
    @Transactional
    @Operation(summary = "축제 등록", description = "부스·무대·공연을 등록하기 전에 축제를 생성합니다.", tags = "관리자 · 축제")
    public ResponseEntity<ApiResponse<FestivalView>> create(@Valid @RequestBody FestivalInput input) {
        access.superAdmin();
        if (!input.endAt().isAfter(input.startAt()))
            throw new ContractException(HttpStatus.BAD_REQUEST, "C001", "축제 종료 시각은 시작 시각보다 늦어야 합니다.");
        var festival = festivals.save(Festival.builder()
                .name(input.name().trim()).description(input.description()).location(input.location())
                .startAt(input.startAt().atZoneSameInstant(KST).toLocalDateTime())
                .endAt(input.endAt().atZoneSameInstant(KST).toLocalDateTime())
                .status(input.status() == null ? FestivalStatus.BEFORE : input.status())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(FestivalView.from(festival)));
    }

    @PatchMapping("/{festivalId}")
    @Transactional
    @Operation(summary = "축제 수정", description = "이름·장소·기간·상태를 바꿉니다. 보내지 않은 값은 유지합니다.", tags = "관리자 · 축제")
    public ApiResponse<FestivalView> edit(@PathVariable long festivalId, @Valid @RequestBody FestivalPatch input) {
        access.superAdmin();
        var festival = festivals.findById(festivalId).orElseThrow(
                () -> new ContractException(HttpStatus.NOT_FOUND, "C001", "축제를 찾을 수 없습니다."));
        if (input.name() != null && input.name().isBlank())
            throw new ContractException(HttpStatus.BAD_REQUEST, "C001", "축제 이름을 입력해 주세요.");
        var start = input.startAt() == null ? festival.getStartAt() : input.startAt().atZoneSameInstant(KST).toLocalDateTime();
        var end = input.endAt() == null ? festival.getEndAt() : input.endAt().atZoneSameInstant(KST).toLocalDateTime();
        if (!end.isAfter(start))
            throw new ContractException(HttpStatus.BAD_REQUEST, "C001", "축제 종료 시각은 시작 시각보다 늦어야 합니다.");
        festival.edit(input.name() == null ? null : input.name().trim(), input.description(), input.location(),
                start, end, input.status());
        return ApiResponse.ok(FestivalView.from(festival));
    }
}
