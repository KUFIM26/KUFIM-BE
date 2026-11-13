package com.example.kufim.lostitem.dto;

import com.example.kufim.lostitem.entity.LostItemCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 습득물 등록 요청. 사진은 먼저 올려 받은 imageKey를 실어 보냅니다. */
public record LostItemCreateRequest(
        @NotNull LostItemCategory category,
        @NotBlank @Size(max = 30) String name,
        @NotBlank @Size(max = 50) String foundPlace,
        @NotNull OffsetDateTime foundAt,
        @Size(max = 30) String storagePlace,
        @Size(max = 300) String description,
        @Size(max = 200) String imageKey,
        Boolean imagePublic) {}
