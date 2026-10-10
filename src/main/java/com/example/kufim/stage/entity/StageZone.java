package com.example.kufim.stage.entity;

import com.example.kufim.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 무대 구역(예: 스탠딩 A구역, 입장 대기열). 이력이 있으면 삭제 대신 비활성화합니다(STG-001). */
@Entity
@Table(name = "stage_zone")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StageZone extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_congestion_log_id")
    private CongestionLog latestLog;

    public void edit(String name, Integer displayOrder, Boolean active) {
        if (name != null) this.name = name;
        if (displayOrder != null) this.displayOrder = displayOrder;
        if (active != null) this.active = active;
    }
    public void recorded(CongestionLog log) { latestLog = log; }
}
