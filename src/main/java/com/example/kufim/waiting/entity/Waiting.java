package com.example.kufim.waiting.entity;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.booth.entity.Booth;
import com.example.kufim.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 웨이팅 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "waiting", uniqueConstraints = {@UniqueConstraint(name = "uk_waiting_booth_number", columnNames = {"booth_id", "waiting_number"})}, indexes = {@Index(name = "idx_waiting_client_status", columnList = "client_token,status"), @Index(name = "idx_waiting_booth_status", columnList = "booth_id,status,registered_at")})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Waiting extends BaseEntity {

    @Column(name = "public_id", nullable = false, length = 36, unique = true)
    @JdbcTypeCode(SqlTypes.CHAR)
    @NotBlank
    @Size(max = 36)
    @Builder.Default
    private String publicId = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booth_id", nullable = false)
    @NotNull
    private Booth booth;

    @Column(name = "client_token", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    @NotBlank
    @Size(max = 36)
    private String clientToken;

    @Column(name = "student_number", nullable = false, length = 9)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Size(max = 9)
    @Pattern(regexp = "[0-9]{9}")
    private String studentNumber;

    @Column(name = "phone_number", nullable = false, length = 11)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Size(max = 11)
    @Pattern(regexp = "[0-9]{11}")
    private String phoneNumber;

    @Column(name = "waiting_number", nullable = false)
    @Min(1)
    private int waitingNumber;

    @Column(name = "party_size", nullable = false)
    @Min(1)
    @Max(6)
    private byte partySize;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    @Builder.Default
    private WaitingStatus status = WaitingStatus.WAITING;

    @Column(name = "privacy_consent", nullable = false)
    @Builder.Default
    private boolean privacyConsent = false;

    @Column(name = "registered_at", nullable = false)
    @NotNull
    private LocalDateTime registeredAt;

    @Column(name = "called_at", nullable = true)
    private LocalDateTime calledAt;

    @Column(name = "auto_cancel_at")
    private LocalDateTime autoCancelAt;

    @Column(name = "last_recalled_at")
    private LocalDateTime lastRecalledAt;

    @Column(name = "recall_count", nullable = false)
    @Builder.Default
    private int recallCount = 0;

    @Column(name = "closed_at", nullable = true)
    private LocalDateTime closedAt;

    @Column(name = "close_reason", length = 30)
    private String closeReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private AdminAccount closedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "called_by", nullable = true)
    private AdminAccount calledBy;

    public void call(AdminAccount actor, LocalDateTime now) {
        if (status == WaitingStatus.WAITING) {
            status = WaitingStatus.CALLED;
            calledAt = now;
            autoCancelAt = now.plusMinutes(10);
            calledBy = actor;
        } else if (status == WaitingStatus.CALLED) {
            lastRecalledAt = now;
            recallCount++;
        } else {
            throw new IllegalStateException("호출할 수 없는 상태입니다.");
        }
    }

    public void close(WaitingStatus next, String reason, AdminAccount actor, LocalDateTime now) {
        status = next;
        closeReason = reason;
        closedBy = actor;
        closedAt = now;
    }

}
