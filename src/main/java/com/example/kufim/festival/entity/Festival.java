package com.example.kufim.festival.entity;

import com.example.kufim.common.entity.BaseEntity;
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

/** 축제 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "festival")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Festival extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String name;

    /** 영어 축제 이름. 이 필드가 응답에 있다는 것이 프런트의 영어 지원 판별 기준입니다. */
    @Column(name = "name_en", length = 40)
    @Size(max = 40)
    private String nameEn;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "start_at", nullable = false)
    @NotNull
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    @NotNull
    private LocalDateTime endAt;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    @Builder.Default
    private FestivalStatus status = FestivalStatus.BEFORE;

    /** 일괄 번역이 빈 영어 칸을 채울 때만 씁니다. */
    public void applyNameEn(String value) { this.nameEn = value; }
    /** null인 값은 그대로 둡니다. 기간 검증은 호출하는 쪽에서 합니다. */
    public void edit(String name, String nameEn, String description, String location, java.time.LocalDateTime startAt,
            java.time.LocalDateTime endAt, FestivalStatus status) {
        if (name != null) this.name = name;
        this.nameEn = com.example.kufim.common.util.EnglishText.apply(this.nameEn, nameEn);
        if (description != null) this.description = description;
        if (location != null) this.location = location;
        if (startAt != null) this.startAt = startAt;
        if (endAt != null) this.endAt = endAt;
        if (status != null) this.status = status;
    }
}
