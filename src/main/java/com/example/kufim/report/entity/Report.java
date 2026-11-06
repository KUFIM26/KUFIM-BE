package com.example.kufim.report.entity;

import com.example.kufim.admin.entity.AdminAccount;
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

/** 긴급 신고 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "report", indexes = {
        @Index(name = "idx_report_festival_status", columnList = "festival_id,status,created_at"),
        @Index(name = "idx_report_client", columnList = "client_token,created_at")})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Report extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    @NotNull
    private Festival festival;

    @Column(name = "client_token", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    @NotBlank
    @Size(max = 36)
    private String clientToken;

    @Column(name = "category", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    private ReportCategory category;

    @Column(name = "title", nullable = false, length = 30)
    @NotBlank
    @Size(max = 30)
    private String title;

    @Column(name = "reporter_name", nullable = false, length = 10)
    @NotBlank
    @Size(max = 10)
    private String reporterName;

    @Column(name = "phone_number", nullable = false, length = 11)
    @NotBlank
    @Size(max = 11)
    private String phoneNumber;

    @Column(name = "place", nullable = false, length = 50)
    @NotBlank
    @Size(max = 50)
    private String place;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    @NotBlank
    private String content;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    private ReportStatus status;

    @Column(name = "admin_note", nullable = true, length = 300)
    @Size(max = 300)
    private String adminNote;

    @Column(name = "status_changed_at", nullable = true)
    private LocalDateTime statusChangedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "status_changed_by", nullable = true)
    private AdminAccount statusChangedBy;

    @Column(name = "received_at", nullable = true)
    private LocalDateTime receivedAt;

    @Column(name = "in_progress_at", nullable = true)
    private LocalDateTime inProgressAt;

    @Column(name = "completed_at", nullable = true)
    private LocalDateTime completedAt;

    @Column(name = "admin_note_updated_at", nullable = true)
    private LocalDateTime adminNoteUpdatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "admin_note_updated_by", nullable = true)
    private AdminAccount adminNoteUpdatedBy;

    /**
     * 상태를 한 단계 옮기고 그 단계의 시각을 남깁니다. 되돌리는 전이는 호출 측에서 막습니다.
     * COMPLETED와 CANCELED는 둘 다 completed_at에 종결 시각을 적습니다.
     */
    public void moveTo(ReportStatus next, AdminAccount actor, LocalDateTime at) {
        this.status = next;
        this.statusChangedAt = at;
        this.statusChangedBy = actor;
        switch (next) {
            case RECEIVED -> this.receivedAt = at;
            case IN_PROGRESS -> this.inProgressAt = at;
            case COMPLETED, CANCELED -> this.completedAt = at;
            default -> { }
        }
    }

    /** 처리 메모를 새로 적습니다. 상태는 바꾸지 않습니다. */
    public void writeNote(String note, AdminAccount actor, LocalDateTime at) {
        this.adminNote = note;
        this.adminNoteUpdatedAt = at;
        this.adminNoteUpdatedBy = actor;
    }

    /** 개인정보 파기. 제목·장소·내용은 운영 기록으로 남깁니다. */
    public void purgePersonalData() {
        this.reporterName = "파기됨";
        this.phoneNumber = "00000000000";
        this.clientToken = "00000000-0000-0000-0000-000000000000";
    }
}
