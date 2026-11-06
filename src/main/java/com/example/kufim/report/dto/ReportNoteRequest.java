package com.example.kufim.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 처리 메모 저장 요청. 저장하면 접수 상태에서 처리 중으로 넘어갑니다. */
public record ReportNoteRequest(
        @NotBlank(message = "처리 메모를 입력해 주세요.")
        @Size(max = 300, message = "처리 메모는 300자 이하로 입력해 주세요.")
        String adminNote) {
}
