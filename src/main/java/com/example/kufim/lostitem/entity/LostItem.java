package com.example.kufim.lostitem.entity;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.common.entity.BaseEntity;
import com.example.kufim.festival.entity.Festival;
import com.example.kufim.report.entity.Report;
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

/**
 * 상황실이 보관 중인 습득물. API에서는 엔터티 대신 서비스의 응답 맵을 사용합니다.
 * 삭제는 기록을 남기기 위해 deleted_at만 채우는 소프트 삭제입니다.
 */
@Entity
@Table(name = "lost_item", indexes = {
        @Index(name = "idx_lost_item_list", columnList = "festival_id,deleted_at,status,created_at"),
        @Index(name = "idx_lost_item_category", columnList = "festival_id,deleted_at,category,created_at")})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LostItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    @NotNull
    private Festival festival;

    @Column(name = "category", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    private LostItemCategory category;

    @Column(name = "name", nullable = false, length = 30)
    @NotBlank
    @Size(max = 30)
    private String name;

    @Column(name = "found_place", nullable = false, length = 50)
    @NotBlank
    @Size(max = 50)
    private String foundPlace;

    @Column(name = "found_at", nullable = false)
    @NotNull
    private LocalDateTime foundAt;

    @Column(name = "storage_place", nullable = false, length = 30)
    @NotBlank
    @Size(max = 30)
    private String storagePlace;

    @Column(name = "description", length = 300)
    @Size(max = 300)
    private String description;

    @Column(name = "image_key", length = 200)
    @Size(max = 200)
    private String imageKey;

    @Column(name = "image_public", nullable = false)
    private boolean imagePublic;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    private LostItemStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registered_by", nullable = false)
    @NotNull
    private AdminAccount registeredBy;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "returned_by")
    private AdminAccount returnedBy;

    @Column(name = "receiver_name", length = 10)
    @Size(max = 10)
    private String receiverName;

    @Column(name = "receiver_phone", length = 11)
    @Size(max = 11)
    private String receiverPhone;

    @Column(name = "verified_by", length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private VerificationMethod verifiedBy;

    @Column(name = "return_note", length = 100)
    @Size(max = 100)
    private String returnNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private Report report;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 등록 내용 수정. null을 넘긴 필드는 바꾸지 않습니다(사진은 별도 메서드). */
    public void edit(LostItemCategory category, String name, String foundPlace, LocalDateTime foundAt,
            String storagePlace, String description, Boolean imagePublic) {
        if (category != null) this.category = category;
        if (name != null) this.name = name;
        if (foundPlace != null) this.foundPlace = foundPlace;
        if (foundAt != null) this.foundAt = foundAt;
        if (storagePlace != null) this.storagePlace = storagePlace;
        this.description = description;
        if (imagePublic != null) this.imagePublic = imagePublic;
        // 분류가 민감하게 바뀌면 공개 설정과 무관하게 비공개로 내립니다.
        if (this.category.sensitive()) this.imagePublic = false;
    }

    public void changeImage(String imageKey) {
        this.imageKey = imageKey;
    }

    /** 인계 완료. 수령인과 확인 수단은 비울 수 없습니다. */
    public void markReturned(String receiverName, String receiverPhone, VerificationMethod verifiedBy,
            String returnNote, AdminAccount actor, LocalDateTime at) {
        this.status = LostItemStatus.RETURNED;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.verifiedBy = verifiedBy;
        this.returnNote = returnNote;
        this.returnedBy = actor;
        this.returnedAt = at;
    }

    /** 인계 취소. 잘못 누른 경우를 되돌리는 용도라 수령인 정보를 모두 지웁니다. */
    public void reopen() {
        this.status = LostItemStatus.STORED;
        this.receiverName = null;
        this.receiverPhone = null;
        this.verifiedBy = null;
        this.returnNote = null;
        this.returnedBy = null;
        this.returnedAt = null;
    }

    public void softDelete(LocalDateTime at) {
        this.deletedAt = at;
        this.imageKey = null;
    }

    /** 축제 종료 후 개인정보 파기. 수령 기록 자체는 통계를 위해 남깁니다. */
    public void purgePersonalData() {
        if (this.receiverName != null) this.receiverName = "파기됨";
        this.receiverPhone = null;
    }

    public boolean deleted() {
        return this.deletedAt != null;
    }
}
