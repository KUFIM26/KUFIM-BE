package com.example.kufim.lostitem.controller;

import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.lostitem.dto.LostItemCreateRequest;
import com.example.kufim.lostitem.dto.LostItemReturnRequest;
import com.example.kufim.lostitem.dto.LostItemUpdateRequest;
import com.example.kufim.lostitem.entity.LostItemCategory;
import com.example.kufim.lostitem.entity.LostItemStatus;
import com.example.kufim.lostitem.service.LostItemService;
import com.example.kufim.security.AdminAccess;
import com.example.kufim.security.RequestRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 관리자용 분실물센터 API. 총괄 관리자만 사용합니다. */
@RestController
@RequestMapping("/api/v1/admin/lost-items")
@RequiredArgsConstructor
@Tag(name = "관리자 · 분실물센터")
public class AdminLostItemApiController {
    private final LostItemService service;
    private final AdminAccess access;
    private final RequestRateLimiter limiter;

    @Value("${app.rate-limit.lost-item-upload-per-admin-per-minute:30}") private int uploadPerAdmin;

    @GetMapping
    @Operation(summary = "습득물 목록",
            description = "status·category를 생략하면 전체입니다. summary는 조건과 무관하게 활성 축제 전체 기준입니다.")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) LostItemCategory category,
            @RequestParam(required = false) LostItemStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        access.superAdmin();
        return ApiResponse.ok(service.adminList(category, status, q, page, size));
    }

    @GetMapping("/{lostItemId}")
    @Operation(summary = "습득물 상세", description = "수령인 이름을 전체로 보여 줍니다.")
    public ApiResponse<Map<String, Object>> one(@PathVariable long lostItemId) {
        access.superAdmin();
        return ApiResponse.ok(service.adminOne(lostItemId));
    }

    @GetMapping("/{lostItemId}/image")
    @Operation(summary = "습득물 사진", description = "비공개 사진과 인계가 끝난 물건의 사진도 볼 수 있습니다.")
    public ResponseEntity<byte[]> image(@PathVariable long lostItemId,
            @RequestParam(defaultValue = "full") String size) {
        access.superAdmin();
        byte[] bytes = service.adminImage(lostItemId, "thumb".equalsIgnoreCase(size));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .body(bytes);
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "사진 업로드",
            description = "JPEG·PNG·WebP 5MB 이하. 저장할 때 EXIF를 지우고 JPEG로 다시 씁니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(@RequestPart("file") MultipartFile file) {
        var actor = access.superAdmin();
        limiter.check("lost-item-upload", String.valueOf(actor.getId()), uploadPerAdmin, "C004");
        return ResponseEntity.status(201).body(ApiResponse.ok(service.uploadImage(file)));
    }

    @PostMapping
    @Operation(summary = "습득물 등록")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody LostItemCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.register(request, access.superAdmin())));
    }

    @PatchMapping("/{lostItemId}")
    @Operation(summary = "습득물 수정",
            description = "imageKey를 보내지 않으면 사진 유지, 문자열이면 교체, null이면 제거입니다.")
    public ApiResponse<Map<String, Object>> edit(@PathVariable long lostItemId,
            @Valid @RequestBody LostItemUpdateRequest request) {
        return ApiResponse.ok(service.edit(lostItemId, request, access.superAdmin()));
    }

    @PostMapping("/{lostItemId}/return")
    @Operation(summary = "인계 완료", description = "보관 중일 때만 가능합니다.")
    public ApiResponse<Map<String, Object>> markReturned(@PathVariable long lostItemId,
            @Valid @RequestBody LostItemReturnRequest request) {
        return ApiResponse.ok(service.markReturned(lostItemId, request, access.superAdmin()));
    }

    @PostMapping("/{lostItemId}/reopen")
    @Operation(summary = "인계 취소", description = "수령인 정보와 인계 시각을 지우고 보관 중으로 되돌립니다.")
    public ApiResponse<Map<String, Object>> reopen(@PathVariable long lostItemId) {
        return ApiResponse.ok(service.reopen(lostItemId, access.superAdmin()));
    }

    @GetMapping("/{lostItemId}/contact")
    @Operation(summary = "수령인 연락처", description = "조회 사실이 이력에 남습니다.")
    public ApiResponse<Map<String, Object>> contact(@PathVariable long lostItemId) {
        return ApiResponse.ok(service.contact(lostItemId, access.superAdmin()));
    }

    @DeleteMapping("/{lostItemId}")
    @Operation(summary = "습득물 삭제", description = "기록은 남기고 목록에서만 감춥니다. 사진 파일은 바로 지웁니다.")
    public ResponseEntity<Void> delete(@PathVariable long lostItemId) {
        service.delete(lostItemId, access.superAdmin());
        return ResponseEntity.noContent().build();
    }
}
