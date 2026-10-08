package com.example.kufim.sms.dto;

import com.example.kufim.sms.entity.SmsMessageType;
import com.example.kufim.sms.entity.SmsSendLog;
import com.example.kufim.sms.entity.SmsStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "문자 발송 이력 조회 응답")
public record SmsSendLogResponse(
        Long id,
        Long waitingId,
        SmsMessageType messageType,
        SmsStatus status,
        String providerId,
        String errorCode,
        Integer cost,
        Long requestedById,
        LocalDateTime sentAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SmsSendLogResponse from(SmsSendLog entity) {
        return new SmsSendLogResponse(
                entity.getId(),
                entity.getWaiting() == null ? null : entity.getWaiting().getId(),
                entity.getMessageType(),
                entity.getStatus(),
                entity.getProviderId(),
                entity.getErrorCode(),
                entity.getCost(),
                entity.getRequestedBy() == null ? null : entity.getRequestedBy().getId(),
                entity.getSentAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
