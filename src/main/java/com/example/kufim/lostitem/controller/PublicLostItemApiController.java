package com.example.kufim.lostitem.controller;

import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.lostitem.entity.LostItemCategory;
import com.example.kufim.lostitem.service.LostItemService;
import com.example.kufim.security.RequestRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/** 방문자용 분실물센터 API. 로그인 없이 조회만 합니다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "분실물센터")
public class PublicLostItemApiController {
    private final LostItemService service;
    private final RequestRateLimiter limiter;

    @Value("${app.rate-limit.lost-item-per-ip-per-minute:120}") private int listPerIp;

    @GetMapping("/lost-items")
    @Operation(summary = "습득물 목록",
            description = "status를 생략하면 보관 중만, ALL이면 전체입니다. q는 2자 이상일 때만 적용됩니다.")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) LostItemCategory category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest http) {
        limiter.check("lost-item-list", http.getRemoteAddr(), listPerIp, "C004");
        return ApiResponse.ok(service.publicList(category, status, q, page, size));
    }

    @GetMapping("/lost-items/{lostItemId}")
    @Operation(summary = "습득물 상세",
            description = "인계가 끝난 물건은 없는 물건과 같은 404로 응답합니다.")
    public ApiResponse<Map<String, Object>> one(@PathVariable long lostItemId) {
        return ApiResponse.ok(service.publicOne(lostItemId));
    }

    @GetMapping("/lost-items/{lostItemId}/image")
    @Operation(summary = "습득물 사진", description = "공개 사진이고 보관 중일 때만 내려줍니다.")
    public ResponseEntity<byte[]> image(@PathVariable long lostItemId,
            @RequestParam(defaultValue = "full") String size) {
        byte[] bytes = service.publicImage(lostItemId, "thumb".equalsIgnoreCase(size));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(bytes);
    }
}
