package com.example.kufim.festival.dto;

import com.example.kufim.festival.entity.Festival;
import com.example.kufim.festival.entity.FestivalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "축제 조회 응답")
public record FestivalResponse(
        Long id,
        String name,
        String description,
        LocalDateTime startAt,
        LocalDateTime endAt,
        FestivalStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FestivalResponse from(Festival entity) {
        return new FestivalResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
