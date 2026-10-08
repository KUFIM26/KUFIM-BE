package com.example.kufim.privacy.dto;

import com.example.kufim.privacy.entity.PrivacyPurgeLog;
import com.example.kufim.privacy.entity.PurgeTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "개인정보 파기 이력 조회 응답")
public record PrivacyPurgeLogResponse(
        Long id,
        Long executedById,
        PurgeTargetType targetType,
        int targetCount,
        LocalDateTime executedAt,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PrivacyPurgeLogResponse from(PrivacyPurgeLog entity) {
        return new PrivacyPurgeLogResponse(
                entity.getId(),
                entity.getExecutedBy() == null ? null : entity.getExecutedBy().getId(),
                entity.getTargetType(),
                entity.getTargetCount(),
                entity.getExecutedAt(),
                entity.getNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
