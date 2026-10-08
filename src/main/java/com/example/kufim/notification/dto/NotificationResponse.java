package com.example.kufim.notification.dto;

import com.example.kufim.notification.entity.Notification;
import com.example.kufim.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "알림 조회 응답")
public record NotificationResponse(
        Long id,
        Long festivalId,
        NotificationType type,
        Long noticeId,
        Long waitingId,
        String title,
        String body,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NotificationResponse from(Notification entity) {
        return new NotificationResponse(
                entity.getId(),
                entity.getFestival() == null ? null : entity.getFestival().getId(),
                entity.getType(),
                entity.getNotice() == null ? null : entity.getNotice().getId(),
                entity.getWaiting() == null ? null : entity.getWaiting().getId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
