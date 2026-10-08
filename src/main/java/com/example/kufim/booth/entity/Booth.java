package com.example.kufim.booth.entity;

import com.example.kufim.common.entity.BaseEntity;
import com.example.kufim.festival.entity.Festival;
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

/** 부스 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "booth", indexes = {@Index(name = "idx_booth_filter", columnList = "festival_id,period,organizer_type,deleted")})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Booth extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    @NotNull
    private Festival festival;

    @Column(name = "name", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String name;

    @Column(name = "organizer_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    @Builder.Default
    private OrganizerType organizerType = OrganizerType.STUDENT_COUNCIL;

    @Column(name = "period", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    @Builder.Default
    private BoothPeriod period = BoothPeriod.DAY;

    @Column(name = "place", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String place;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    @Column(name = "operating_hours", nullable = true, length = 100)
    @Size(max = 100)
    private String operatingHours;

    @Column(name = "booth_code", nullable = true, length = 12, unique = true)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Size(max = 12)
    private String boothCode;

    @Column(name = "qr_issued_at", nullable = true)
    private LocalDateTime qrIssuedAt;

    @Column(name = "waiting_enabled", nullable = false)
    @Builder.Default
    private boolean waitingEnabled = false;

    @Column(name = "accepting_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    @Builder.Default
    private AcceptingStatus acceptingStatus = AcceptingStatus.OPEN;

    @Column(name = "estimate_minutes_per_team", nullable = false)
    @Min(1)
    @Max(360)
    @Builder.Default
    private int estimateMinutesPerTeam = 5;

    @Column(name = "auto_cancel_minutes", nullable = false)
    @Min(1)
    @Max(360)
    @Builder.Default
    private int autoCancelMinutes = 10;

    @Column(name = "waiting_seq", nullable = false)
    @Min(0)
    @Builder.Default
    private int waitingSeq = 0;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    public int nextWaitingNumber() { return ++waitingSeq; }
    public void configure(AcceptingStatus status, Integer estimate) {
        if (status != null) acceptingStatus = status;
        if (estimate != null) estimateMinutesPerTeam = estimate;
    }
    public void edit(String name, OrganizerType organizerType, BoothPeriod period, String place,
            String description, String operatingHours, Boolean waitingEnabled) {
        if (name != null) this.name = name;
        if (organizerType != null) this.organizerType = organizerType;
        if (period != null) this.period = period;
        if (place != null) this.place = place;
        if (description != null) this.description = description;
        if (operatingHours != null) this.operatingHours = operatingHours;
        if (waitingEnabled != null) this.waitingEnabled = waitingEnabled;
    }
    public void issueQr(String code, LocalDateTime issuedAt) { boothCode = code; qrIssuedAt = issuedAt; }
    public void delete() { deleted = true; }

}
