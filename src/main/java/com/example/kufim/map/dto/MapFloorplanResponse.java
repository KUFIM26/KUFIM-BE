package com.example.kufim.map.dto;

import com.example.kufim.map.entity.MapFloorplan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "지도 도면 조회 응답")
public record MapFloorplanResponse(
        Long id,
        Long festivalId,
        String imageUrl,
        int width,
        int height,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MapFloorplanResponse from(MapFloorplan entity) {
        return new MapFloorplanResponse(
                entity.getId(),
                entity.getFestival() == null ? null : entity.getFestival().getId(),
                entity.getImageUrl(),
                entity.getWidth(),
                entity.getHeight(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
