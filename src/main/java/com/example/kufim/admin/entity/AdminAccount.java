package com.example.kufim.admin.entity;

import com.example.kufim.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 관리자 계정 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "admin_account")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AdminAccount extends BaseEntity {

    @Column(name = "login_id", nullable = false, length = 50, unique = true)
    @NotBlank
    @Size(max = 50)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    @NotBlank
    @Size(max = 50)
    private String name;

    @Column(name = "role", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    @Builder.Default
    private AdminRole role = AdminRole.BOOTH_ADMIN;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_login_at", nullable = true)
    private LocalDateTime lastLoginAt;

    @Column(name = "session_version", nullable = false)
    @Builder.Default
    private int sessionVersion = 0;

    public void loggedIn(LocalDateTime time) { lastLoginAt = time; }
    public void change(String name, AdminRole role, Boolean active) {
        if (name != null) this.name = name;
        if (role != null) this.role = role;
        if (active != null) this.active = active;
    }
    public void resetPassword(String hash) { passwordHash = hash; sessionVersion++; }
    public void forceLogout() { sessionVersion++; }

}
