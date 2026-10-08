package com.example.kufim.facility.entity;

import com.example.kufim.common.entity.BaseEntity;
import com.example.kufim.festival.entity.Festival;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 편의시설 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "facility")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Facility extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    @NotNull
    private Festival festival;

    @Column(name = "type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    @Builder.Default
    private FacilityType type = FacilityType.CONTROL_ROOM;

    @Column(name = "name", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String name;

    @Column(name = "description", nullable = true, length = 255)
    @Size(max = 255)
    private String description;

    @Column(name = "place", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String place;

    @Column(name = "operating_hours", nullable = true, length = 100)
    @Size(max = 100)
    private String operatingHours;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    public void edit(FacilityType type, String name, String description, String place, String operatingHours) {
        if (type != null) this.type = type;
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (place != null) this.place = place;
        if (operatingHours != null) this.operatingHours = operatingHours;
    }
    public void delete() { deleted = true; }

}
