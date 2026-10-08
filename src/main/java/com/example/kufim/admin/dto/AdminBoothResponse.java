package com.example.kufim.admin.dto;

import com.example.kufim.admin.entity.AdminBooth;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 부스 배정 조회 응답")
public record AdminBoothResponse(
        Long id,
        Long adminAccountId,
        Long boothId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminBoothResponse from(AdminBooth entity) {
        return new AdminBoothResponse(
                entity.getId(),
                entity.getAdminAccount() == null ? null : entity.getAdminAccount().getId(),
                entity.getBooth() == null ? null : entity.getBooth().getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
