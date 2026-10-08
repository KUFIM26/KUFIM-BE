package com.example.kufim.notification.controller;

import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.notification.entity.Notification;
import com.example.kufim.notification.entity.NotificationRead;
import com.example.kufim.notification.entity.NotificationType;
import com.example.kufim.notification.repository.NotificationReadRepository;
import com.example.kufim.notification.repository.NotificationRepository;
import com.example.kufim.waiting.service.ClientToken;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class PersonalNotificationApiController {
    private final NotificationRepository notifications;
    private final NotificationReadRepository reads;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> list(@RequestHeader(value = "X-Client-Token", required = false) String rawToken,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        String token = ClientToken.require(rawToken);
        var result = notifications.visible(token, ApiClock.now(), PageRequest.of(page, size));
        var content = result.getContent().stream().map(n -> view(n, token)).toList();
        return ApiResponse.ok(Map.of("content", content, "page", page, "size", size,
                "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages(),
                "unreadCount", notifications.unreadCount(token, ApiClock.now())));
    }

    @PatchMapping("/{notificationId}/read")
    @Transactional
    public ApiResponse<Map<String, Object>> read(@PathVariable long notificationId,
            @RequestHeader(value = "X-Client-Token", required = false) String rawToken) {
        String token = ClientToken.require(rawToken);
        var n = notifications.lockById(notificationId).orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없습니다."));
        if (n.getType() == NotificationType.NOTICE && (n.getNotice().isDeleted() || n.getNotice().getPublishedAt().isAfter(ApiClock.now())))
            throw new ContractException(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없습니다.");
        if (n.getType() != NotificationType.NOTICE && !token.equals(n.getRecipientClientToken()))
            throw new ContractException(HttpStatus.FORBIDDEN, "W003", "알림 소유권이 없습니다.");
        var existing = reads.findByNotificationIdAndClientToken(notificationId, token);
        var row = existing.orElseGet(() -> reads.save(NotificationRead.builder().notification(n).clientToken(token).readAt(ApiClock.now()).build()));
        return ApiResponse.ok(Map.of("notificationId", notificationId, "readAt", ApiClock.offset(row.getReadAt())));
    }

    public record ReadAllRequest(Long throughNotificationId) {}

    @PatchMapping("/read-all")
    @Transactional
    public ApiResponse<Map<String, Object>> readAll(@RequestHeader(value = "X-Client-Token", required = false) String rawToken,
            @RequestBody(required = false) ReadAllRequest body) {
        String token = ClientToken.require(rawToken);
        long through = body != null && body.throughNotificationId() != null ? body.throughNotificationId() :
                notifications.visible(token, ApiClock.now(), PageRequest.of(0, 1)).stream().findFirst().map(Notification::getId).orElse(0L);
        int count = 0;
        for (var n : notifications.visibleThrough(token, ApiClock.now(), through)) {
            var locked = notifications.lockById(n.getId()).orElseThrow();
            if (reads.findByNotificationIdAndClientToken(locked.getId(), token).isEmpty()) {
                reads.save(NotificationRead.builder().notification(locked).clientToken(token).readAt(ApiClock.now()).build());
                count++;
            }
        }
        return ApiResponse.ok(Map.of("readCount", count, "unreadCount", notifications.unreadCount(token, ApiClock.now())));
    }

    private Map<String, Object> view(Notification n, String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notificationId", n.getId()); result.put("type", n.getType()); result.put("title", n.getTitle());
        result.put("body", n.getBody()); result.put("createdAt", ApiClock.offset(n.getCreatedAt()));
        result.put("readAt", reads.findByNotificationIdAndClientToken(n.getId(), token)
                .map(r -> ApiClock.offset(r.getReadAt())).orElse(null));
        result.put("noticeId", n.getNotice() == null ? null : n.getNotice().getId());
        result.put("waitingId", n.getWaiting() == null ? null : n.getWaiting().getPublicId());
        return result;
    }
}
