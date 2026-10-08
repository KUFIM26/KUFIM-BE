package com.example.kufim.sms.entity;

/** 문자 발송 이력에서 사용하는 상태·분류 값. DB에는 문자열로 저장합니다. */
public enum SmsStatus {
    SENT, FAILED, SKIPPED_LIMIT, NOT_CONFIGURED
}
