package com.example.kufim.stage.service;

import com.example.kufim.common.dto.PageResponse;
import com.example.kufim.common.exception.ResourceNotFoundException;
import com.example.kufim.stage.dto.StageResponse;
import com.example.kufim.stage.repository.StageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 무대 조회 계층. 등록·변경 규칙은 이 계층에서 트랜잭션으로 추가합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StageService {
    private final StageRepository repository;

    public PageResponse<StageResponse> findAll(int page, int size) {
        return PageResponse.from(repository.findAll(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(StageResponse::from));
    }

    public StageResponse findById(Long id) {
        return StageResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("무대", id)));
    }
}
