package com.example.kufim.map.entity;

import com.example.kufim.common.entity.BaseEntity;
import com.example.kufim.map.entity.MapFloorplan;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 지도 위치 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "map_pin", uniqueConstraints = {@UniqueConstraint(name = "uk_map_pin_target", columnNames = {"target_type", "target_id"})})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MapPin extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floorplan_id", nullable = false)
    @NotNull
    private MapFloorplan floorplan;

    @Column(name = "target_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    private MapTargetType targetType;

    @Column(name = "target_id", nullable = false)
    @NotNull
    @Min(1)
    private Long targetId;

    @Column(name = "x_percent", nullable = false, precision = 5, scale = 2)
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal xPercent;

    @Column(name = "y_percent", nullable = false, precision = 5, scale = 2)
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal yPercent;

    public void move(BigDecimal x, BigDecimal y) { xPercent = x; yPercent = y; }

}
