package com.example.kufim.audit.dto;

import com.example.kufim.audit.entity.AuditLog;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 조작 이력 조회 응답")
public record AuditLogResponse(
        Long id,
        Long adminAccountId,
        String action,
        String targetType,
        String targetId,
        LocalDateTime occurredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AuditLogResponse from(AuditLog entity) {
        return new AuditLogResponse(
                entity.getId(),
                entity.getAdminAccount() == null ? null : entity.getAdminAccount().getId(),
                entity.getAction(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getOccurredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
