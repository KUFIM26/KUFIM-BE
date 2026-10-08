package com.example.kufim.waiting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record WaitingCreateRequest(
        @NotNull @Pattern(regexp = "[0-9]{9}", message = "학번은 숫자 9자리여야 합니다.") String studentNumber,
        @NotNull @Pattern(regexp = "[0-9]{11}", message = "전화번호는 숫자 11자리여야 합니다.") String phoneNumber,
        @NotNull @Min(1) @Max(6) Integer partySize,
        Boolean privacyConsent) {}
