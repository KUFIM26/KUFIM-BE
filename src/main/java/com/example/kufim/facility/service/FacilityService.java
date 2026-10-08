package com.example.kufim.facility.service;

import com.example.kufim.common.dto.PageResponse;
import com.example.kufim.common.exception.ResourceNotFoundException;
import com.example.kufim.facility.dto.FacilityResponse;
import com.example.kufim.facility.repository.FacilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 편의시설 조회 계층. 등록·변경 규칙은 이 계층에서 트랜잭션으로 추가합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityService {
    private final FacilityRepository repository;

    public PageResponse<FacilityResponse> findAll(int page, int size) {
        return PageResponse.from(repository.findAllByDeletedFalse(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(FacilityResponse::from));
    }

    public FacilityResponse findById(Long id) {
        return FacilityResponse.from(repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("편의시설", id)));
    }
}
