package com.example.kufim.privacy.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 개인정보 파기 이력 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "privacy_purge_log")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PrivacyPurgeLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "executed_by", nullable = false)
    @NotNull
    private AdminAccount executedBy;

    @Column(name = "target_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    private PurgeTargetType targetType;

    @Column(name = "target_count", nullable = false)
    @Min(0)
    private int targetCount;

    @Column(name = "executed_at", nullable = false)
    @NotNull
    private LocalDateTime executedAt;

    @Column(name = "note", nullable = true, length = 255)
    @Size(max = 255)
    private String note;

}
