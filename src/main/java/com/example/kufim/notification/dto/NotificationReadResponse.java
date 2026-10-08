package com.example.kufim.notification.dto;

import com.example.kufim.notification.entity.NotificationRead;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "알림 읽음 이력 조회 응답")
public record NotificationReadResponse(
        Long id,
        Long notificationId,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NotificationReadResponse from(NotificationRead entity) {
        return new NotificationReadResponse(
                entity.getId(),
                entity.getNotification() == null ? null : entity.getNotification().getId(),
                entity.getReadAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
