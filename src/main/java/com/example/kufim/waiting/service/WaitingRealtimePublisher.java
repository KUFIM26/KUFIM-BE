package com.example.kufim.waiting.service;

import com.example.kufim.common.util.ApiClock;
import com.example.kufim.waiting.entity.Waiting;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@RequiredArgsConstructor
public class WaitingRealtimePublisher {
    private static final Logger log = LoggerFactory.getLogger(WaitingRealtimePublisher.class);
    private final SimpMessagingTemplate messaging;

    public void changed(Waiting w) {
        Map<String, Object> personal = new LinkedHashMap<>();
        personal.put("type", "WAITING_UPDATED"); personal.put("waitingId", w.getPublicId()); personal.put("status", w.getStatus());
        personal.put("calledAt", ApiClock.offset(w.getCalledAt())); personal.put("lastRecalledAt", ApiClock.offset(w.getLastRecalledAt()));
        personal.put("recallCount", w.getRecallCount()); personal.put("autoCancelAt", ApiClock.offset(w.getAutoCancelAt()));
        personal.put("occurredAt", ApiClock.offset(ApiClock.now()));
        long boothId = w.getBooth().getId();
        afterCommit(() -> {
            messaging.convertAndSend("/topic/waiting/" + w.getPublicId(), (Object) personal);
            messaging.convertAndSend("/topic/booth/" + boothId + "/waiting", (Object) Map.of("type", "WAITING_STATUS_CHANGED", "boothId", boothId));
            messaging.convertAndSend("/topic/admin/booth/" + boothId + "/waiting", (Object) Map.of("type", "WAITING_LIST_CHANGED", "boothId", boothId));
        });
    }
    /** 바뀐 구역 하나만 보냅니다. 1,000명에게 전체 스냅샷을 보내지 않습니다(STG-005). */
    public void congestion(Map<String, Object> zone) {
        afterCommit(() -> messaging.convertAndSend("/topic/congestion", (Object) zone));
    }
    public void notice(String action, long id) {
        afterCommit(() -> messaging.convertAndSend("/topic/notice", (Object) Map.of("type", action, "noticeId", id)));
    }
    private void afterCommit(Runnable work) {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { work.run(); } catch (RuntimeException failure) { log.warn("실시간 알림 전송 실패. 클라이언트는 GET으로 재조회해야 합니다.", failure); }
                }
            });
        else work.run();
    }
}
