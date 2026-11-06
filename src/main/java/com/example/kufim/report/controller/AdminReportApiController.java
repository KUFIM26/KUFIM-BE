package com.example.kufim.report.controller;

import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.report.dto.ReportCompleteRequest;
import com.example.kufim.report.dto.ReportNoteRequest;
import com.example.kufim.report.entity.ReportStatus;
import com.example.kufim.report.service.ReportService;
import com.example.kufim.security.AdminAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 관리자용 긴급 신고 API. 총괄 관리자만 사용합니다.
 * 상태는 직접 고르지 않고 상세 열기·메모 저장·완료 처리 동작에 따라 바뀝니다.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@Tag(name = "관리자 · 긴급 신고")
public class AdminReportApiController {
    private final ReportService service;
    private final AdminAccess access;

    @GetMapping
    @Operation(summary = "신고 목록", description = "status를 생략하면 전체입니다. 최신순.")
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        access.superAdmin();
        return ApiResponse.ok(service.adminList(status, page, size));
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "신고 상세", description = "조회만으로는 상태가 바뀌지 않습니다.")
    public ApiResponse<Map<String, Object>> one(@PathVariable long reportId) {
        access.superAdmin();
        return ApiResponse.ok(service.adminOne(reportId));
    }

    @PostMapping("/{reportId}/receive")
    @Operation(summary = "접수", description = "상세 화면을 열 때 호출합니다. 신청 상태일 때만 접수로 넘어갑니다.")
    public ApiResponse<Map<String, Object>> receive(@PathVariable long reportId) {
        return ApiResponse.ok(service.receive(reportId, access.superAdmin()));
    }

    @PutMapping("/{reportId}/memo")
    @Operation(summary = "처리 메모 저장", description = "접수 상태에서 저장하면 처리 중으로 넘어갑니다.")
    public ApiResponse<Map<String, Object>> memo(@PathVariable long reportId,
            @Valid @RequestBody ReportNoteRequest request) {
        return ApiResponse.ok(service.saveMemo(reportId, request, access.superAdmin()));
    }

    @PostMapping("/{reportId}/complete")
    @Operation(summary = "완료 처리", description = "접수 또는 처리 중 상태에서 완료로 넘깁니다.")
    public ApiResponse<Map<String, Object>> complete(@PathVariable long reportId,
            @Valid @RequestBody(required = false) ReportCompleteRequest request) {
        return ApiResponse.ok(service.complete(reportId, request, access.superAdmin()));
    }

    @GetMapping("/{reportId}/contact")
    @Operation(summary = "신고자 연락처", description = "조회 사실이 이력에 남습니다.")
    public ApiResponse<Map<String, Object>> contact(@PathVariable long reportId) {
        return ApiResponse.ok(service.contact(reportId, access.superAdmin()));
    }
}
