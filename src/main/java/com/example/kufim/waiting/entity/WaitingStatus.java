package com.example.kufim.waiting.entity;

/** 웨이팅에서 사용하는 상태·분류 값. DB에는 문자열로 저장합니다. */
public enum WaitingStatus {
    WAITING, CALLED, ENTERED, CANCELED, AUTO_CANCELED, NO_SHOW
}
