package com.example.kufim.waiting.dto;

import com.example.kufim.waiting.entity.ActorType;
import com.example.kufim.waiting.entity.WaitingEvent;
import com.example.kufim.waiting.entity.WaitingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "웨이팅 상태 이력 조회 응답")
public record WaitingEventResponse(
        Long id,
        Long waitingId,
        WaitingStatus fromStatus,
        WaitingStatus toStatus,
        ActorType actorType,
        Long actorId,
        LocalDateTime occurredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static WaitingEventResponse from(WaitingEvent entity) {
        return new WaitingEventResponse(
                entity.getId(),
                entity.getWaiting() == null ? null : entity.getWaiting().getId(),
                entity.getFromStatus(),
                entity.getToStatus(),
                entity.getActorType(),
                entity.getActorId(),
                entity.getOccurredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
