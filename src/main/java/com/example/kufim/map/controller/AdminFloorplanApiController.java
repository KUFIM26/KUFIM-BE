package com.example.kufim.map.controller;

import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.festival.repository.FestivalRepository;
import com.example.kufim.map.entity.MapFloorplan;
import com.example.kufim.map.repository.MapFloorplanRepository;
import com.example.kufim.security.AdminAccess;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/map/floorplans")
@RequiredArgsConstructor
public class AdminFloorplanApiController {
    private final AdminAccess access;
    private final FestivalRepository festivals;
    private final MapFloorplanRepository floorplans;

    public record FloorplanInput(@NotNull Long festivalId, @NotBlank @Size(max = 500) String imageUrl,
            @Min(1) int width, @Min(1) int height) {}

    public record FloorplanView(long floorplanId, long festivalId, String imageUrl, int width, int height) {
        static FloorplanView from(MapFloorplan floorplan) {
            return new FloorplanView(floorplan.getId(), floorplan.getFestival().getId(),
                    floorplan.getImageUrl(), floorplan.getWidth(), floorplan.getHeight());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "지도 도면 목록 조회", tags = "관리자 · 지도")
    public ApiResponse<List<FloorplanView>> list() {
        access.superAdmin();
        return ApiResponse.ok(floorplans.findAll().stream()
                .sorted(Comparator.comparingLong(MapFloorplan::getId).reversed())
                .map(FloorplanView::from).toList());
    }

    @PostMapping
    @Transactional
    @Operation(summary = "축제 지도 도면 등록", description = "위치가 포함된 부스·시설을 등록하기 전에 축제별 도면을 한 번 등록합니다. 기존 도면 교체는 지원하지 않습니다.", tags = "관리자 · 지도")
    public ResponseEntity<ApiResponse<FloorplanView>> create(@Valid @RequestBody FloorplanInput input) {
        access.superAdmin();
        var festival = festivals.findById(input.festivalId()).orElseThrow(
                () -> new ContractException(HttpStatus.NOT_FOUND, "C001", "축제를 찾을 수 없습니다."));
        if (floorplans.existsByFestivalId(input.festivalId()))
            throw new ContractException(HttpStatus.CONFLICT, "M004", "이 축제의 지도 도면이 이미 등록되어 있습니다.");
        var floorplan = floorplans.save(MapFloorplan.builder().festival(festival)
                .imageUrl(input.imageUrl()).width(input.width()).height(input.height()).build());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(FloorplanView.from(floorplan)));
    }
}
