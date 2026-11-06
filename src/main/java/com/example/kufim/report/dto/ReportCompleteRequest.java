package com.example.kufim.report.dto;

import jakarta.validation.constraints.Size;

/** 완료 처리 요청. 메모는 선택이며, 값이 있으면 함께 갱신합니다. */
public record ReportCompleteRequest(
        @Size(max = 300, message = "처리 메모는 300자 이하로 입력해 주세요.")
        String adminNote) {
}
