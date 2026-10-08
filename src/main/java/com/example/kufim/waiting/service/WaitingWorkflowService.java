package com.example.kufim.waiting.service;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.booth.entity.AcceptingStatus;
import com.example.kufim.booth.entity.Booth;
import com.example.kufim.booth.repository.BoothRepository;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.notification.entity.Notification;
import com.example.kufim.notification.entity.NotificationType;
import com.example.kufim.notification.repository.NotificationRepository;
import com.example.kufim.sms.entity.SmsMessageType;
import com.example.kufim.sms.entity.SmsSendLog;
import com.example.kufim.sms.entity.SmsStatus;
import com.example.kufim.sms.repository.SmsSendLogRepository;
import tools.jackson.databind.ObjectMapper;
import com.example.kufim.waiting.dto.WaitingCreateRequest;
import com.example.kufim.waiting.entity.ActorType;
import com.example.kufim.waiting.entity.Waiting;
import com.example.kufim.waiting.entity.WaitingEvent;
import com.example.kufim.waiting.entity.WaitingStatus;
import com.example.kufim.waiting.repository.WaitingEventRepository;
import com.example.kufim.waiting.repository.WaitingRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WaitingWorkflowService {
    private final WaitingRepository waitings;
    private final WaitingEventRepository events;
    private final BoothRepository booths;
    private final NotificationRepository notifications;
    private final JdbcTemplate jdbc;
    private final WaitingRealtimePublisher realtime;
    private final SmsSendLogRepository smsLogs;
    private final ObjectMapper json;

    @Transactional
    public Map<String, Object> register(long boothId, String rawToken, String key, WaitingCreateRequest input) {
        String token = ClientToken.require(rawToken);
        if (!Boolean.TRUE.equals(input.privacyConsent()))
            throw new ContractException(HttpStatus.BAD_REQUEST, "C002", "개인정보 동의가 필요합니다.");
        if (key != null && !key.isBlank()) {
            if (key.length() > 100) throw new ContractException(HttpStatus.BAD_REQUEST, "C001", "재시도 키가 너무 깁니다.");
            List<String> snapshots = jdbc.queryForList("select response_json from waiting_idempotency where client_token=? and request_key=?", String.class, token, key);
            if (!snapshots.isEmpty() && snapshots.get(0) != null) return snapshot(snapshots.get(0));
            if (!snapshots.isEmpty()) {
                var ids = jdbc.queryForList("select w.public_id from waiting_idempotency i join waiting w on w.id=i.waiting_id where i.client_token=? and i.request_key=?", String.class, token, key);
                return ticket(requireOwned(ids.get(0), token));
            }
        }
        Booth booth = booths.lockActive(boothId).orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND, "B001", "부스를 찾을 수 없습니다."));
        if (!booth.isWaitingEnabled()) throw new ContractException(HttpStatus.CONFLICT, "W005", "웨이팅 기능을 사용하지 않는 부스입니다.");
        if (booth.getAcceptingStatus() != AcceptingStatus.OPEN) throw new ContractException(HttpStatus.CONFLICT, "W002", "현재 대기를 접수하지 않습니다.");
        List<Waiting> active = waitings.findByClientTokenAndStatusIn(token, List.of(WaitingStatus.WAITING, WaitingStatus.CALLED));
        if (!active.isEmpty()) throw duplicate(active.get(0));
        LocalDateTime now = ApiClock.now();
        Waiting waiting = waitings.saveAndFlush(Waiting.builder().publicId(UUID.randomUUID().toString())
                .booth(booth).clientToken(token).studentNumber(input.studentNumber()).phoneNumber(input.phoneNumber())
                .partySize(input.partySize().byteValue()).privacyConsent(true)
                .waitingNumber(booth.nextWaitingNumber()).registeredAt(now).build());
        try {
            jdbc.update("insert into active_waiting (client_token,waiting_id) values (?,?)", token, waiting.getId());
        } catch (DataIntegrityViolationException conflict) {
            throw new ContractException(HttpStatus.CONFLICT, "W001", "이미 활성 대기표가 있습니다.");
        }
        event(waiting, null, WaitingStatus.WAITING, ActorType.USER, null, now);
        realtime.changed(waiting);
        var result = ticket(waiting);
        if (key != null && !key.isBlank())
            jdbc.update("insert into waiting_idempotency (client_token,request_key,waiting_id,response_json) values (?,?,?,?)",
                    token, key, waiting.getId(), json.writeValueAsString(result));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(String rawToken) {
        String token = ClientToken.require(rawToken);
        return waitings.findByClientTokenAndStatusIn(token, List.of(WaitingStatus.WAITING, WaitingStatus.CALLED))
                .stream().map(this::ticket).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> owned(String publicId, String rawToken) {
        return ticket(requireOwned(publicId, ClientToken.require(rawToken)));
    }

    @Transactional
    public void cancelOwned(String publicId, String rawToken) {
        String token = ClientToken.require(rawToken);
        Waiting w = lock(publicId);
        if (!w.getClientToken().equals(token)) throw new ContractException(HttpStatus.FORBIDDEN, "W003", "대기표 소유권이 없습니다.");
        close(w, WaitingStatus.CANCELED, "USER_CANCEL", null, ActorType.USER);
    }

    @Transactional
    public Map<String, Object> call(String publicId, AdminAccount actor) { return call(publicId, actor, null); }

    @Transactional
    public Map<String, Object> call(String publicId, AdminAccount actor, String key) {
        Waiting w = lock(publicId);
        if (key != null && !key.isBlank()) {
            if (key.length() > 100) throw new ContractException(HttpStatus.BAD_REQUEST,"C001","재시도 키가 너무 깁니다.");
            var snapshots = jdbc.queryForList("select response_json from waiting_call_idempotency where waiting_id=? and request_key=?", String.class, w.getId(), key);
            if (!snapshots.isEmpty()) return snapshot(snapshots.get(0));
        }
        if (w.getStatus() != WaitingStatus.WAITING && w.getStatus() != WaitingStatus.CALLED)
            throw new ContractException(HttpStatus.CONFLICT, "W007", "호출할 수 없는 상태입니다.");
        if (w.getStatus() == WaitingStatus.WAITING && w.getBooth().getAcceptingStatus() == AcceptingStatus.CLOSED)
            throw new ContractException(HttpStatus.CONFLICT, "W007", "운영 종료된 부스에서 새 호출을 할 수 없습니다.");
        WaitingStatus before = w.getStatus();
        LocalDateTime now = ApiClock.now();
        w.call(actor, now);
        event(w, before, WaitingStatus.CALLED, ActorType.ADMIN, actor.getId(), now);
        realtime.changed(w);
        notifications.save(Notification.builder().festival(w.getBooth().getFestival()).type(NotificationType.WAITING_CALL)
                .waiting(w).recipientClientToken(w.getClientToken()).title("부스 입장 호출")
                .body(w.getBooth().getName() + "에서 입장할 차례입니다.").build());
        smsLogs.save(SmsSendLog.builder().waiting(w).messageType(before == WaitingStatus.WAITING ? SmsMessageType.CALL : SmsMessageType.RESEND)
                .status(SmsStatus.NOT_CONFIGURED).errorCode("PROVIDER_NOT_CONFIGURED").requestedBy(actor).sentAt(now).build());
        Map<String, Object> result = ticket(w);
        // No provider is configured. Report this honestly; a sent record must only follow a provider ack.
        result.put("smsStatus", "NOT_CONFIGURED");
        if (key != null && !key.isBlank())
            jdbc.update("insert into waiting_call_idempotency (waiting_id,request_key,response_json) values (?,?,?)",
                    w.getId(), key, json.writeValueAsString(result));
        return result;
    }

    @Transactional
    public Map<String, Object> adminClose(String publicId, AdminAccount actor, WaitingStatus target) {
        Waiting w = lock(publicId);
        if (target == WaitingStatus.CANCELED) close(w, target, "ADMIN_CANCEL", actor, ActorType.ADMIN);
        else {
            if (w.getStatus() != WaitingStatus.CALLED)
                throw new ContractException(HttpStatus.CONFLICT, "W008", "호출된 대기표만 처리할 수 있습니다.");
            close(w, target, target.name(), actor, ActorType.ADMIN);
        }
        return ticket(w);
    }

    @Transactional
    public boolean autoCancel(String publicId) {
        Waiting w = lock(publicId);
        if (w.getStatus() != WaitingStatus.CALLED || w.getAutoCancelAt() == null || w.getAutoCancelAt().isAfter(ApiClock.now())) return false;
        close(w, WaitingStatus.AUTO_CANCELED, "CALL_TIMEOUT", null, ActorType.SYSTEM);
        notifications.save(Notification.builder().festival(w.getBooth().getFestival()).type(NotificationType.WAITING_AUTO_CANCELED)
                .waiting(w).recipientClientToken(w.getClientToken()).title("웨이팅 자동 취소")
                .body("호출 후 10분이 지나 대기표가 자동 취소되었습니다.").build());
        return true;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(long boothId, String rawToken) {
        Booth booth = booths.findByIdAndDeletedFalse(boothId).orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND, "B001", "부스를 찾을 수 없습니다."));
        long count = waitings.countByBoothIdAndStatus(boothId, WaitingStatus.WAITING);
        String mine = null;
        if (rawToken != null && !rawToken.isBlank()) {
            var found = waitings.findByClientTokenAndStatusIn(ClientToken.require(rawToken), List.of(WaitingStatus.WAITING, WaitingStatus.CALLED));
            if (!found.isEmpty()) mine = found.get(0).getPublicId();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("boothId", boothId); result.put("waitingEnabled", booth.isWaitingEnabled());
        result.put("acceptingStatus", booth.getAcceptingStatus());
        result.put("effectiveWaitingStatus", booth.isWaitingEnabled() ? booth.getAcceptingStatus().name() : "DISABLED");
        result.put("waitingTeamCount", count); result.put("estimatedWaitMinutes", count * booth.getEstimateMinutesPerTeam());
        result.put("estimationAvailable", true); result.put("myActiveWaitingId", mine); result.put("serverTime", ApiClock.offset(ApiClock.now()));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ticket(Waiting w) {
        long ahead = w.getStatus() == WaitingStatus.WAITING
                ? waitings.findByBoothIdAndStatusInOrderByRegisteredAtAsc(w.getBooth().getId(), List.of(WaitingStatus.WAITING))
                    .stream().takeWhile(row -> !row.getId().equals(w.getId())).count() : 0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("waitingId", w.getPublicId()); result.put("boothId", w.getBooth().getId()); result.put("boothName", w.getBooth().getName());
        result.put("waitingNumber", w.getWaitingNumber()); result.put("partySize", w.getPartySize()); result.put("status", w.getStatus());
        result.put("aheadTeamCount", ahead); result.put("position", w.getStatus() == WaitingStatus.WAITING ? ahead + 1 : null);
        result.put("estimatedWaitMinutes", ahead * w.getBooth().getEstimateMinutesPerTeam()); result.put("estimationAvailable", true);
        result.put("registeredAt", ApiClock.offset(w.getRegisteredAt())); result.put("calledAt", ApiClock.offset(w.getCalledAt()));
        result.put("lastRecalledAt", ApiClock.offset(w.getLastRecalledAt())); result.put("recallCount", w.getRecallCount());
        result.put("autoCancelAt", ApiClock.offset(w.getAutoCancelAt())); result.put("closedAt", ApiClock.offset(w.getClosedAt()));
        result.put("closeReason", w.getCloseReason()); result.put("smsEnabled", true); result.put("serverTime", ApiClock.offset(ApiClock.now()));
        return result;
    }

    public Waiting find(String publicId) { return waitings.findByPublicId(publicId).orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND, "W004", "대기표를 찾을 수 없습니다.")); }
    private Waiting requireOwned(String publicId, String token) {
        Waiting w = find(publicId);
        if (!w.getClientToken().equals(token)) throw new ContractException(HttpStatus.FORBIDDEN, "W003", "대기표 소유권이 없습니다.");
        return w;
    }
    private Waiting lock(String publicId) { return waitings.lockByPublicId(publicId).orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND, "W004", "대기표를 찾을 수 없습니다.")); }
    private ContractException duplicate(Waiting w) { return new ContractException(HttpStatus.CONFLICT, "W001", "이미 활성 대기표가 있습니다.", Map.of("waitingId", w.getPublicId())); }
    private void close(Waiting w, WaitingStatus target, String reason, AdminAccount actor, ActorType actorType) {
        if (w.getStatus() != WaitingStatus.WAITING && w.getStatus() != WaitingStatus.CALLED)
            throw new ContractException(HttpStatus.CONFLICT, "W006", "이미 종료된 대기표입니다.");
        LocalDateTime now = ApiClock.now();
        WaitingStatus before = w.getStatus();
        w.close(target, reason, actor, now);
        jdbc.update("delete from active_waiting where client_token=? and waiting_id=?", w.getClientToken(), w.getId());
        event(w, before, target, actorType, actor == null ? null : actor.getId(), now);
        realtime.changed(w);
    }
    private void event(Waiting w, WaitingStatus from, WaitingStatus to, ActorType actorType, Long actorId, LocalDateTime now) {
        events.save(WaitingEvent.builder().waiting(w).fromStatus(from).toStatus(to).actorType(actorType).actorId(actorId).occurredAt(now).build());
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshot(String serialized) { return json.readValue(serialized, Map.class); }
}
