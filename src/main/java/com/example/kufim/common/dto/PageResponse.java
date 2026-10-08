package com.example.kufim.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** 페이지 번호는 0부터 시작하며, size는 1~100을 허용합니다. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements,
                              int totalPages, boolean hasNext) {
    public static <T> PageResponse<T> from(Page<T> result) {
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }
}
