package com.example.kufim.admin.service;

import com.example.kufim.common.dto.PageResponse;
import com.example.kufim.common.exception.ResourceNotFoundException;
import com.example.kufim.admin.dto.AdminBoothResponse;
import com.example.kufim.admin.repository.AdminBoothRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 부스 배정 조회 계층. 등록·변경 규칙은 이 계층에서 트랜잭션으로 추가합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBoothService {
    private final AdminBoothRepository repository;

    public PageResponse<AdminBoothResponse> findAll(int page, int size) {
        return PageResponse.from(repository.findAll(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(AdminBoothResponse::from));
    }

    public AdminBoothResponse findById(Long id) {
        return AdminBoothResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("관리자 부스 배정", id)));
    }
}
