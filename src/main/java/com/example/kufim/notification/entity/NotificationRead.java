package com.example.kufim.notification.entity;

import com.example.kufim.common.entity.BaseEntity;
import com.example.kufim.notification.entity.Notification;
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

/** 알림 읽음 이력 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "notification_read", uniqueConstraints = {@UniqueConstraint(name = "uk_notification_read_client", columnNames = {"notification_id", "client_token"})})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationRead extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    @NotNull
    private Notification notification;

    @Column(name = "client_token", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    @NotBlank
    @Size(max = 36)
    private String clientToken;

    @Column(name = "read_at", nullable = false)
    @NotNull
    private LocalDateTime readAt;

}
