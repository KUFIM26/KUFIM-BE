package com.example.kufim.stage.entity;

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

/** 무대 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "stage")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Stage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    @NotNull
    private Festival festival;

    @Column(name = "name", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String name;

    @Column(name = "name_en", length = 40)
    @Size(max = 40)
    private String nameEn;

    @Column(name = "description", nullable = true, length = 255)
    @Size(max = 255)
    private String description;

    @Column(name = "entry_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    @Builder.Default
    private EntryStatus entryStatus = EntryStatus.OPEN;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    /** 일괄 번역이 빈 영어 칸을 채울 때만 씁니다. */
    public void applyNameEn(String value) { this.nameEn = value; }
    public void edit(String name, String nameEn, String description, Integer displayOrder) {
        if (name != null) this.name = name;
        this.nameEn = com.example.kufim.common.util.EnglishText.apply(this.nameEn, nameEn);
        if (description != null) this.description = description;
        if (displayOrder != null) this.displayOrder = displayOrder;
    }
    public void entry(EntryStatus status) { entryStatus = status; }

}
