package com.example.kufim.stage.dto;

import com.example.kufim.stage.entity.EntryStatus;
import com.example.kufim.stage.entity.Stage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "무대 조회 응답")
public record StageResponse(
        Long id,
        Long festivalId,
        String name,
        String description,
        EntryStatus entryStatus,
        int displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static StageResponse from(Stage entity) {
        return new StageResponse(
                entity.getId(),
                entity.getFestival() == null ? null : entity.getFestival().getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getEntryStatus(),
                entity.getDisplayOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
