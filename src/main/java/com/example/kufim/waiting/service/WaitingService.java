package com.example.kufim.waiting.service;

import com.example.kufim.common.dto.PageResponse;
import com.example.kufim.common.exception.ResourceNotFoundException;
import com.example.kufim.waiting.dto.WaitingResponse;
import com.example.kufim.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 웨이팅 조회 계층. 등록·변경 규칙은 이 계층에서 트랜잭션으로 추가합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitingService {
    private final WaitingRepository repository;

    public PageResponse<WaitingResponse> findAll(int page, int size) {
        return PageResponse.from(repository.findAll(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(WaitingResponse::from));
    }

    public WaitingResponse findById(Long id) {
        return WaitingResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("웨이팅", id)));
    }
}
