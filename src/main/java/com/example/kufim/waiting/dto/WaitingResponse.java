package com.example.kufim.waiting.dto;

import com.example.kufim.common.util.PersonalDataMasker;
import com.example.kufim.waiting.entity.Waiting;
import com.example.kufim.waiting.entity.WaitingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "웨이팅 조회 응답")
public record WaitingResponse(
        Long id,
        String publicId,
        Long boothId,
        String studentNumber,
        String phoneNumber,
        int waitingNumber,
        byte partySize,
        WaitingStatus status,
        LocalDateTime registeredAt,
        LocalDateTime calledAt,
        LocalDateTime closedAt,
        Long calledById,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static WaitingResponse from(Waiting entity) {
        return new WaitingResponse(
                entity.getId(),
                entity.getPublicId(),
                entity.getBooth() == null ? null : entity.getBooth().getId(),
                PersonalDataMasker.studentNumber(entity.getStudentNumber()),
                PersonalDataMasker.phoneNumber(entity.getPhoneNumber()),
                entity.getWaitingNumber(),
                entity.getPartySize(),
                entity.getStatus(),
                entity.getRegisteredAt(),
                entity.getCalledAt(),
                entity.getClosedAt(),
                entity.getCalledBy() == null ? null : entity.getCalledBy().getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
