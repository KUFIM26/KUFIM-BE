package com.example.kufim.report.controller;

import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.waiting.service.ClientToken;
import com.example.kufim.report.dto.ReportCreateRequest;
import com.example.kufim.report.service.ReportService;
import com.example.kufim.security.RequestRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 방문자용 긴급 신고 API. 로그인 없이 X-Client-Token으로 본인 신고를 구분합니다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "긴급 신고")
public class PublicReportApiController {
    private final ReportService service;
    private final RequestRateLimiter limiter;

    @Value("${app.rate-limit.report-per-token-per-minute:3}") private int reportPerToken;
    @Value("${app.rate-limit.report-per-ip-per-minute:20}") private int reportPerIp;

    @PostMapping("/reports")
    @Operation(summary = "신고 등록")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @RequestHeader(value = "X-Client-Token", required = false) String token,
            @Valid @RequestBody ReportCreateRequest request, HttpServletRequest http) {
        String validToken = ClientToken.require(token);
        limiter.check("report-token", validToken, reportPerToken, "C004");
        limiter.check("report-ip", http.getRemoteAddr(), reportPerIp, "C004");
        return ResponseEntity.status(201).body(ApiResponse.ok(service.register(token, request)));
    }

    @GetMapping("/reports/me")
    @Operation(summary = "내 신고 목록")
    public ApiResponse<Map<String, Object>> mine(
            @RequestHeader(value = "X-Client-Token", required = false) String token) {
        List<Map<String, Object>> content = service.mine(token);
        return ApiResponse.ok(Map.of("content", content));
    }

    @GetMapping("/reports/{reportId}")
    @Operation(summary = "내 신고 상세")
    public ApiResponse<Map<String, Object>> one(@PathVariable long reportId,
            @RequestHeader(value = "X-Client-Token", required = false) String token) {
        return ApiResponse.ok(service.owned(reportId, token));
    }

    @DeleteMapping("/reports/{reportId}")
    @Operation(summary = "내 신고 취소", description = "접수 전(신청) 상태인 신고만 취소할 수 있습니다.")
    public ResponseEntity<Void> cancel(@PathVariable long reportId,
            @RequestHeader(value = "X-Client-Token", required = false) String token) {
        service.cancelOwned(reportId, token);
        return ResponseEntity.noContent().build();
    }
}
