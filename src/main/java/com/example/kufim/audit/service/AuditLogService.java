package com.example.kufim.audit.service;

import com.example.kufim.common.dto.PageResponse;
import com.example.kufim.common.exception.ResourceNotFoundException;
import com.example.kufim.audit.dto.AuditLogResponse;
import com.example.kufim.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 조작 이력 조회 계층. 등록·변경 규칙은 이 계층에서 트랜잭션으로 추가합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogService {
    private final AuditLogRepository repository;

    public PageResponse<AuditLogResponse> findAll(int page, int size) {
        return PageResponse.from(repository.findAll(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(AuditLogResponse::from));
    }

    public AuditLogResponse findById(Long id) {
        return AuditLogResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("관리자 조작 이력", id)));
    }
}
