package com.example.kufim.waiting.entity;

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

/** 웨이팅 상태 이력 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "waiting_event", indexes = {@Index(name = "idx_waiting_event_time", columnList = "waiting_id,occurred_at")})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WaitingEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "waiting_id", nullable = false)
    @NotNull
    private Waiting waiting;

    @Column(name = "from_status", nullable = true, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private WaitingStatus fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    private WaitingStatus toStatus;

    @Column(name = "actor_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    private ActorType actorType;

    @Column(name = "actor_id", nullable = true)
    private Long actorId;

    @Column(name = "occurred_at", nullable = false)
    @NotNull
    private LocalDateTime occurredAt;

}
