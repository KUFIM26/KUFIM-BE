package com.example.kufim.waiting.controller;

import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.waiting.dto.WaitingCreateRequest;
import com.example.kufim.waiting.service.WaitingWorkflowService;
import com.example.kufim.waiting.service.ClientToken;
import com.example.kufim.security.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublicWaitingApiController {
    private final WaitingWorkflowService service;
    private final RequestRateLimiter limiter;

    @GetMapping("/booths/{boothId}/waiting-status")
    public ApiResponse<Map<String, Object>> status(@PathVariable long boothId,
            @RequestHeader(value = "X-Client-Token", required = false) String token) {
        return ApiResponse.ok(service.status(boothId, token));
    }

    @PostMapping("/booths/{boothId}/waitings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@PathVariable long boothId,
            @RequestHeader(value = "X-Client-Token", required = false) String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody WaitingCreateRequest request, HttpServletRequest http) {
        String validToken = ClientToken.require(token);
        limiter.check("waiting-token", validToken, 10, "C004");
        limiter.check("waiting-ip", http.getRemoteAddr(), 60, "C004");
        return ResponseEntity.status(201).body(ApiResponse.ok(service.register(boothId, token, key, request)));
    }

    @GetMapping("/waitings/me")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestHeader(value = "X-Client-Token", required = false) String token) {
        return ApiResponse.ok(service.mine(token));
    }

    @GetMapping("/waitings/{waitingId}")
    public ApiResponse<Map<String, Object>> one(@PathVariable String waitingId,
            @RequestHeader(value = "X-Client-Token", required = false) String token) {
        return ApiResponse.ok(service.owned(waitingId, token));
    }

    @DeleteMapping("/waitings/{waitingId}")
    public ResponseEntity<Void> cancel(@PathVariable String waitingId,
            @RequestHeader(value = "X-Client-Token", required = false) String token) {
        ClientToken.require(token);
        service.autoCancel(waitingId);
        service.cancelOwned(waitingId, token);
        return ResponseEntity.noContent().build();
    }
}
