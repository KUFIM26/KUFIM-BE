package com.example.kufim.stage.entity;

import com.example.kufim.admin.entity.AdminAccount;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 혼잡도 입력 이력. append-only이므로 수정 메서드를 두지 않습니다(STG-008). */
@Entity
@Table(name = "congestion_log")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CongestionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_zone_id", nullable = false)
    private StageZone zone;

    @Column(name = "level", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private CongestionLevel level;

    @Column(name = "estimated_people")
    private Integer estimatedPeople;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private AdminAccount recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
