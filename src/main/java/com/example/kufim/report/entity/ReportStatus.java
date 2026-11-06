package com.example.kufim.report.entity;

/**
 * 신고 처리 상태. 관리자가 직접 고르지 않고 동작에 따라 바뀝니다.
 * SUBMITTED -> RECEIVED -> IN_PROGRESS -> COMPLETED 이며, 사용자 취소는 CANCELED로 종결합니다.
 * 되돌릴 수 없고, COMPLETED·CANCELED에서는 더 바꿀 수 없습니다.
 */
public enum ReportStatus {
    SUBMITTED("신청"), RECEIVED("접수"), IN_PROGRESS("처리 중"), COMPLETED("완료"), CANCELED("취소");

    private final String label;

    ReportStatus(String label) { this.label = label; }

    /** 알림·문자 문구에 쓰는 한글 상태명. */
    public String label() { return label; }

    /** 더 이상 상태를 바꿀 수 없는 종결 상태인지. */
    public boolean closed() { return this == COMPLETED || this == CANCELED; }
}
