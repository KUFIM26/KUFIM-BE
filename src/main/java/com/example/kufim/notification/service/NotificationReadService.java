package com.example.kufim.notification.service;

import com.example.kufim.common.dto.PageResponse;
import com.example.kufim.common.exception.ResourceNotFoundException;
import com.example.kufim.notification.dto.NotificationReadResponse;
import com.example.kufim.notification.repository.NotificationReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 읽음 이력 조회 계층. 등록·변경 규칙은 이 계층에서 트랜잭션으로 추가합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationReadService {
    private final NotificationReadRepository repository;

    public PageResponse<NotificationReadResponse> findAll(int page, int size) {
        return PageResponse.from(repository.findAll(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(NotificationReadResponse::from));
    }

    public NotificationReadResponse findById(Long id) {
        return NotificationReadResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("알림 읽음 이력", id)));
    }
}
