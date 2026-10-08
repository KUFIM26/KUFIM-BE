package com.example.kufim.waiting.service;

import com.example.kufim.common.dto.PageResponse;
import com.example.kufim.common.exception.ResourceNotFoundException;
import com.example.kufim.waiting.dto.WaitingEventResponse;
import com.example.kufim.waiting.repository.WaitingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 웨이팅 상태 이력 조회 계층. 등록·변경 규칙은 이 계층에서 트랜잭션으로 추가합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitingEventService {
    private final WaitingEventRepository repository;

    public PageResponse<WaitingEventResponse> findAll(int page, int size) {
        return PageResponse.from(repository.findAll(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(WaitingEventResponse::from));
    }

    public WaitingEventResponse findById(Long id) {
        return WaitingEventResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("웨이팅 상태 이력", id)));
    }
}
