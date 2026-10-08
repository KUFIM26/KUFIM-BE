package com.example.kufim.map.service;

import com.example.kufim.common.dto.PageResponse;
import com.example.kufim.common.exception.ResourceNotFoundException;
import com.example.kufim.map.dto.MapFloorplanResponse;
import com.example.kufim.map.repository.MapFloorplanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지도 도면 조회 계층. 등록·변경 규칙은 이 계층에서 트랜잭션으로 추가합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapFloorplanService {
    private final MapFloorplanRepository repository;

    public PageResponse<MapFloorplanResponse> findAll(int page, int size) {
        return PageResponse.from(repository.findAll(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(MapFloorplanResponse::from));
    }

    public MapFloorplanResponse findById(Long id) {
        return MapFloorplanResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("지도 도면", id)));
    }
}
