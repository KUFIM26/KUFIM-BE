package com.example.kufim.lostitem.dto;

import com.example.kufim.lostitem.entity.LostItemCategory;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 습득물 수정 요청. 보내지 않은 필드는 그대로 둡니다.
 *
 * imageKey만 세 가지로 해석해야 합니다. 필드가 없으면 사진 유지, 문자열이면 교체, null이면 제거.
 * 레코드로는 "없음"과 "null"을 구분할 수 없어, 설정자가 불렸는지를 따로 기억하는 클래스로 둡니다.
 * (Jackson은 JSON에 키가 있을 때만 설정자를 부르므로 명시적 null도 구분됩니다.)
 */
@Getter
public class LostItemUpdateRequest {
    @Setter
    private LostItemCategory category;

    @Setter
    @Size(max = 30)
    private String name;

    @Setter
    @Size(max = 50)
    private String foundPlace;

    @Setter
    private OffsetDateTime foundAt;

    @Setter
    @Size(max = 30)
    private String storagePlace;

    @Size(max = 300)
    private String description;

    private boolean descriptionPresent;

    @Setter
    private Boolean imagePublic;

    @Size(max = 200)
    private String imageKey;

    private boolean imageKeyPresent;

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
        this.imageKeyPresent = true;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    /** JSON에 imageKey 키가 들어 있었는지. 설정자에서만 켜집니다. */
    public boolean imageKeyPresent() {
        return imageKeyPresent;
    }

    public boolean descriptionPresent() {
        return descriptionPresent;
    }
}
