package com.example.kufim.waiting.service;

import com.example.kufim.common.util.ApiClock;
import com.example.kufim.waiting.entity.WaitingStatus;
import com.example.kufim.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WaitingAutoCancelJob {
    private final WaitingRepository repository;
    private final WaitingWorkflowService workflow;
    @Scheduled(fixedDelayString = "${app.waiting.auto-cancel-poll-ms:5000}")
    public void run() {
        repository.findByStatusAndAutoCancelAtLessThanEqual(WaitingStatus.CALLED, ApiClock.now())
                .forEach(w -> workflow.autoCancel(w.getPublicId()));
    }
}
