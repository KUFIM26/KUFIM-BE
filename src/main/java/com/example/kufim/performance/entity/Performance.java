package com.example.kufim.performance.entity;

import com.example.kufim.common.entity.BaseEntity;
import com.example.kufim.festival.entity.Festival;
import com.example.kufim.stage.entity.Stage;
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

/** 공연 프로그램 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "performance", indexes = {@Index(name = "idx_performance_festival_time", columnList = "festival_id,start_at")})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Performance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    @NotNull
    private Festival festival;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    @NotNull
    private Stage stage;

    @Column(name = "title", nullable = false, length = 200)
    @NotBlank
    @Size(max = 200)
    private String title;

    @Column(name = "type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    @Builder.Default
    private PerformanceType type = PerformanceType.OTHER;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    @Column(name = "additional_description", nullable = true, columnDefinition = "TEXT")
    private String additionalDescription;

    @Column(name = "cast_members", nullable = true, columnDefinition = "TEXT")
    private String castMembers;

    @Column(name = "setlist", nullable = true, columnDefinition = "TEXT")
    private String setlist;

    @Column(name = "start_at", nullable = false)
    @NotNull
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    @NotNull
    private LocalDateTime endAt;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    public void edit(Stage stage, String title, PerformanceType type, String description,
            String additionalDescription, String castMembers, String setlist,
            LocalDateTime startAt, LocalDateTime endAt, Integer displayOrder) {
        if (stage != null) this.stage = stage;
        if (title != null) this.title = title;
        if (type != null) this.type = type;
        if (description != null) this.description = description;
        if (additionalDescription != null) this.additionalDescription = additionalDescription;
        if (castMembers != null) this.castMembers = castMembers;
        if (setlist != null) this.setlist = setlist;
        if (startAt != null) this.startAt = startAt;
        if (endAt != null) this.endAt = endAt;
        if (displayOrder != null) this.displayOrder = displayOrder;
    }
    public void delete() { deleted = true; }

}
