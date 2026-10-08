package com.example.kufim.admin.dto;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.admin.entity.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 계정 조회 응답")
public record AdminAccountResponse(
        Long id,
        String loginId,
        String name,
        AdminRole role,
        boolean active,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminAccountResponse from(AdminAccount entity) {
        return new AdminAccountResponse(
                entity.getId(),
                entity.getLoginId(),
                entity.getName(),
                entity.getRole(),
                entity.isActive(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
