package com.example.kufim.audit.entity;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자 조작 이력 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "audit_log")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "admin_account_id", nullable = true)
    private AdminAccount adminAccount;

    @Column(name = "action", nullable = false, length = 50)
    @NotBlank
    @Size(max = 50)
    private String action;

    @Column(name = "target_type", nullable = true, length = 30)
    @Size(max = 30)
    private String targetType;

    @Column(name = "target_id", nullable = true, length = 64)
    @Size(max = 64)
    private String targetId;

    @Column(name = "detail", nullable = true, columnDefinition = "TEXT")
    private String detail;

    @Column(name = "ip", nullable = true, length = 45)
    @Size(max = 45)
    private String ip;

    @Column(name = "occurred_at", nullable = false)
    @NotNull
    private LocalDateTime occurredAt;

}
