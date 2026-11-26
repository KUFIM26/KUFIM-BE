package com.example.kufim.notice.entity;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.common.entity.BaseEntity;
import com.example.kufim.festival.entity.Festival;
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

/** 공지사항 데이터. API에서는 엔터티 대신 dto의 응답 객체를 사용합니다. */
@Entity
@Table(name = "notice", indexes = {@Index(name = "idx_notice_published", columnList = "festival_id,published_at")})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Notice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    @NotNull
    private Festival festival;

    @Column(name = "category", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    @Builder.Default
    private NoticeCategory category = NoticeCategory.INFO;

    @Column(name = "title", nullable = false, length = 200)
    @NotBlank
    @Size(max = 200)
    private String title;

    @Column(name = "title_en", length = 60)
    @Size(max = 60)
    private String titleEn;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    @NotBlank
    private String content;

    @Column(name = "content_en", length = 2000)
    @Size(max = 2000)
    private String contentEn;

    @Column(name = "published_at", nullable = false)
    @NotNull
    private LocalDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "created_by", nullable = true)
    private AdminAccount createdBy;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /** 일괄 번역이 빈 영어 칸을 채울 때만 씁니다. */
    public void applyTitleEn(String value) { this.titleEn = value; }
    public void applyContentEn(String value) { this.contentEn = value; }
    public void edit(NoticeCategory category, String title, String titleEn, String content, String contentEn) {
        if (category != null) this.category = category;
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        this.titleEn = com.example.kufim.common.util.EnglishText.apply(this.titleEn, titleEn);
        this.contentEn = com.example.kufim.common.util.EnglishText.apply(this.contentEn, contentEn);
    }
    public void delete() { deleted = true; }

}
