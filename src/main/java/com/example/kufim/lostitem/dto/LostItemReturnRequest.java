package com.example.kufim.lostitem.dto;

import com.example.kufim.lostitem.entity.VerificationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 인계 완료 처리 요청. 수령인 이름과 확인 수단은 반드시 남깁니다. */
public record LostItemReturnRequest(
        @NotBlank @Size(max = 10) String receiverName,
        @Pattern(regexp = "^$|^01[0-9]{8,9}$", message = "전화번호 형식을 확인해 주세요.") String receiverPhone,
        @NotNull VerificationMethod verifiedBy,
        @Size(max = 100) String returnNote) {}
