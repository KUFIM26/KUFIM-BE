package com.example.kufim.privacy.service;

import com.example.kufim.common.dto.PageResponse;
import com.example.kufim.common.exception.ResourceNotFoundException;
import com.example.kufim.privacy.dto.PrivacyPurgeLogResponse;
import com.example.kufim.privacy.repository.PrivacyPurgeLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 개인정보 파기 이력 조회 계층. 등록·변경 규칙은 이 계층에서 트랜잭션으로 추가합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrivacyPurgeLogService {
    private final PrivacyPurgeLogRepository repository;

    public PageResponse<PrivacyPurgeLogResponse> findAll(int page, int size) {
        return PageResponse.from(repository.findAll(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(PrivacyPurgeLogResponse::from));
    }

    public PrivacyPurgeLogResponse findById(Long id) {
        return PrivacyPurgeLogResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("개인정보 파기 이력", id)));
    }
}
