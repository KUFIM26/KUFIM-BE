package com.example.kufim.notification.entity;

import com.example.kufim.common.entity.BaseEntity;
import com.example.kufim.festival.entity.Festival;
import com.example.kufim.notice.entity.Notice;
import com.example.kufim.waiting.entity.Waiting;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 알림 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "notification", indexes = {@Index(name = "idx_notification_recipient", columnList = "recipient_client_token,created_at")})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    @NotNull
    private Festival festival;

    @Column(name = "type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "notice_id", nullable = true)
    private Notice notice;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "waiting_id", nullable = true)
    private Waiting waiting;

    @Column(name = "recipient_client_token", nullable = true, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Size(max = 36)
    private String recipientClientToken;

    @Column(name = "title", nullable = false, length = 200)
    @NotBlank
    @Size(max = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    @NotBlank
    private String body;

    public void updateContent(String title, String body) { this.title = title; this.body = body; }

}
