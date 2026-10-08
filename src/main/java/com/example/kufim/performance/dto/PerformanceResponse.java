package com.example.kufim.performance.dto;

import com.example.kufim.performance.entity.Performance;
import com.example.kufim.performance.entity.PerformanceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "공연 프로그램 조회 응답")
public record PerformanceResponse(
        Long id,
        Long festivalId,
        Long stageId,
        String title,
        PerformanceType type,
        String description,
        String additionalDescription,
        String castMembers,
        String setlist,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PerformanceResponse from(Performance entity) {
        return new PerformanceResponse(
                entity.getId(),
                entity.getFestival() == null ? null : entity.getFestival().getId(),
                entity.getStage() == null ? null : entity.getStage().getId(),
                entity.getTitle(),
                entity.getType(),
                entity.getDescription(),
                entity.getAdditionalDescription(),
                entity.getCastMembers(),
                entity.getSetlist(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.getDisplayOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
