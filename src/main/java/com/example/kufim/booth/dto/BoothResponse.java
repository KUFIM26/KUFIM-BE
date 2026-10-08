package com.example.kufim.booth.dto;

import com.example.kufim.booth.entity.AcceptingStatus;
import com.example.kufim.booth.entity.Booth;
import com.example.kufim.booth.entity.BoothPeriod;
import com.example.kufim.booth.entity.OrganizerType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "부스 조회 응답")
public record BoothResponse(
        Long id,
        Long festivalId,
        String name,
        OrganizerType organizerType,
        BoothPeriod period,
        String place,
        String description,
        String operatingHours,
        boolean waitingEnabled,
        AcceptingStatus acceptingStatus,
        int estimateMinutesPerTeam,
        int autoCancelMinutes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BoothResponse from(Booth entity) {
        return new BoothResponse(
                entity.getId(),
                entity.getFestival() == null ? null : entity.getFestival().getId(),
                entity.getName(),
                entity.getOrganizerType(),
                entity.getPeriod(),
                entity.getPlace(),
                entity.getDescription(),
                entity.getOperatingHours(),
                entity.isWaitingEnabled(),
                entity.getAcceptingStatus(),
                entity.getEstimateMinutesPerTeam(),
                entity.getAutoCancelMinutes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
