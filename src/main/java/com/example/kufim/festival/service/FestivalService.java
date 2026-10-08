package com.example.kufim.festival.service;

import com.example.kufim.common.dto.PageResponse;
import com.example.kufim.common.exception.ResourceNotFoundException;
import com.example.kufim.festival.dto.FestivalResponse;
import com.example.kufim.festival.repository.FestivalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 축제 조회 계층. 등록·변경 규칙은 이 계층에서 트랜잭션으로 추가합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FestivalService {
    private final FestivalRepository repository;

    public PageResponse<FestivalResponse> findAll(int page, int size) {
        return PageResponse.from(repository.findAll(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(FestivalResponse::from));
    }

    public FestivalResponse findById(Long id) {
        return FestivalResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("축제", id)));
    }
}
