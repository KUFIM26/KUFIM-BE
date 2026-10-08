package com.example.kufim.sms.service;

import com.example.kufim.common.dto.PageResponse;
import com.example.kufim.common.exception.ResourceNotFoundException;
import com.example.kufim.sms.dto.SmsSendLogResponse;
import com.example.kufim.sms.repository.SmsSendLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문자 발송 이력 조회 계층. 등록·변경 규칙은 이 계층에서 트랜잭션으로 추가합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SmsSendLogService {
    private final SmsSendLogRepository repository;

    public PageResponse<SmsSendLogResponse> findAll(int page, int size) {
        return PageResponse.from(repository.findAll(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(SmsSendLogResponse::from));
    }

    public SmsSendLogResponse findById(Long id) {
        return SmsSendLogResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("문자 발송 이력", id)));
    }
}
