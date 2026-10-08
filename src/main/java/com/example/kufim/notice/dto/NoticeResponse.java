package com.example.kufim.notice.dto;

import com.example.kufim.notice.entity.Notice;
import com.example.kufim.notice.entity.NoticeCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "공지사항 조회 응답")
public record NoticeResponse(
        Long id,
        Long festivalId,
        NoticeCategory category,
        String title,
        String content,
        LocalDateTime publishedAt,
        Long createdById,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NoticeResponse from(Notice entity) {
        return new NoticeResponse(
                entity.getId(),
                entity.getFestival() == null ? null : entity.getFestival().getId(),
                entity.getCategory(),
                entity.getTitle(),
                entity.getContent(),
                entity.getPublishedAt(),
                entity.getCreatedBy() == null ? null : entity.getCreatedBy().getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
