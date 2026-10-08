package com.example.kufim.sms.entity;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.common.entity.BaseEntity;
import com.example.kufim.waiting.entity.Waiting;
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

/** 문자 발송 이력 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "sms_send_log")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SmsSendLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "waiting_id", nullable = false)
    @NotNull
    private Waiting waiting;

    @Column(name = "message_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    private SmsMessageType messageType;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    private SmsStatus status;

    @Column(name = "provider_id", nullable = true, length = 64)
    @Size(max = 64)
    private String providerId;

    @Column(name = "error_code", nullable = true, length = 40)
    @Size(max = 40)
    private String errorCode;

    @Column(name = "cost", nullable = true)
    @Min(0)
    private Integer cost;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "requested_by", nullable = true)
    private AdminAccount requestedBy;

    @Column(name = "sent_at", nullable = false)
    @NotNull
    private LocalDateTime sentAt;

}
