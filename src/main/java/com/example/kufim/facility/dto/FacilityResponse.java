package com.example.kufim.facility.dto;

import com.example.kufim.facility.entity.Facility;
import com.example.kufim.facility.entity.FacilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "편의시설 조회 응답")
public record FacilityResponse(
        Long id,
        Long festivalId,
        FacilityType type,
        String name,
        String description,
        String place,
        String operatingHours,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FacilityResponse from(Facility entity) {
        return new FacilityResponse(
                entity.getId(),
                entity.getFestival() == null ? null : entity.getFestival().getId(),
                entity.getType(),
                entity.getName(),
                entity.getDescription(),
                entity.getPlace(),
                entity.getOperatingHours(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
