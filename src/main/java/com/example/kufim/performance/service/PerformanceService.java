package com.example.kufim.performance.service;

import com.example.kufim.common.dto.PageResponse;
import com.example.kufim.common.exception.ResourceNotFoundException;
import com.example.kufim.performance.dto.PerformanceResponse;
import com.example.kufim.performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공연 프로그램 조회 계층. 등록·변경 규칙은 이 계층에서 트랜잭션으로 추가합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerformanceService {
    private final PerformanceRepository repository;

    public PageResponse<PerformanceResponse> findAll(int page, int size) {
        return PageResponse.from(repository.findAllByDeletedFalse(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(PerformanceResponse::from));
    }

    public PerformanceResponse findById(Long id) {
        return PerformanceResponse.from(repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("공연 프로그램", id)));
    }
}
