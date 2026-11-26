package com.example.kufim.map.entity;

import com.example.kufim.common.entity.BaseEntity;
import com.example.kufim.festival.entity.Festival;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 지도 도면 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "map_floorplan")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MapFloorplan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    @NotNull
    private Festival festival;

    @Column(name = "image_url", nullable = false, length = 500)
    @NotBlank
    @Size(max = 500)
    private String imageUrl;

    /** 글자만 영어로 바꾼 같은 크기·같은 배치의 도면. 없으면 한국어 도면을 씁니다. */
    @Column(name = "image_url_en", length = 500)
    @Size(max = 500)
    private String imageUrlEn;

    @Column(name = "width", nullable = false)
    @Min(1)
    private int width;

    @Column(name = "height", nullable = false)
    @Min(1)
    private int height;

    /** 도면 이미지만 바꿉니다. 핀은 이미지 기준 퍼센트 좌표라 그대로 유지됩니다. */
    public void replace(String imageUrl, String imageUrlEn, int width, int height) {
        this.imageUrl = imageUrl;
        this.imageUrlEn = com.example.kufim.common.util.EnglishText.apply(this.imageUrlEn, imageUrlEn);
        this.width = width;
        this.height = height;
    }
}
