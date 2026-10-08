package com.example.kufim.map.dto;

import com.example.kufim.map.entity.MapPin;
import com.example.kufim.map.entity.MapTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "지도 위치 조회 응답")
public record MapPinResponse(
        Long id,
        Long floorplanId,
        MapTargetType targetType,
        Long targetId,
        BigDecimal xPercent,
        BigDecimal yPercent,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MapPinResponse from(MapPin entity) {
        return new MapPinResponse(
                entity.getId(),
                entity.getFloorplan() == null ? null : entity.getFloorplan().getId(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getXPercent(),
                entity.getYPercent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
