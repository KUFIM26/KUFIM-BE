package com.example.kufim.report.dto;

import com.example.kufim.report.entity.ReportCategory;
import jakarta.validation.constraints.*;

/** 방문자 신고 등록 요청. 개인정보 동의는 값만 확인하고 저장하지 않습니다. */
public record ReportCreateRequest(
        @NotNull(message = "신고 유형을 선택해 주세요.") ReportCategory category,
        @NotBlank @Size(max = 30, message = "제목은 30자 이하로 입력해 주세요.") String title,
        @NotBlank @Size(max = 10, message = "이름은 10자 이하로 입력해 주세요.") String reporterName,
        @NotNull @Pattern(regexp = "[0-9]{11}", message = "전화번호는 숫자 11자리여야 합니다.") String phoneNumber,
        @NotBlank @Size(max = 50, message = "장소는 50자 이하로 입력해 주세요.") String place,
        @NotBlank @Size(max = 300, message = "내용은 300자 이하로 입력해 주세요.") String content,
        Boolean privacyConsent) {
}
