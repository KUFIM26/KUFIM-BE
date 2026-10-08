package com.example.kufim.audit.controller;

import com.example.kufim.audit.repository.AuditLogRepository;
import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.security.AdminAccess;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AuditApiController {
    private final AdminAccess access;
    private final AuditLogRepository logs;
    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        access.superAdmin();
        var result = logs.findAll(PageRequest.of(page, size, Sort.by("occurredAt").descending()));
        var content = result.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("auditLogId", a.getId()); row.put("adminAccountId", a.getAdminAccount() == null ? null : a.getAdminAccount().getId());
            row.put("action", a.getAction()); row.put("targetType", a.getTargetType()); row.put("targetId", a.getTargetId());
            row.put("detail", a.getDetail()); row.put("occurredAt", ApiClock.offset(a.getOccurredAt()));
            return row;
        }).toList();
        return ApiResponse.ok(Map.of("content", content, "page", page, "size", size,
                "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages()));
    }
}
