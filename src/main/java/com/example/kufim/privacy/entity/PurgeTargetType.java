package com.example.kufim.privacy.entity;

/** 개인정보 파기 이력에서 사용하는 상태·분류 값. DB에는 문자열로 저장합니다. */
public enum PurgeTargetType {
    STUDENT_NUMBER, PHONE_NUMBER, CLIENT_TOKEN, ACCESS_LOG
}
