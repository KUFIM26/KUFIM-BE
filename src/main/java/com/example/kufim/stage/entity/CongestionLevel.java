package com.example.kufim.stage.entity;

/** 무대 구역 혼잡도 4단계. 15분이 지난 값의 "정보 확인 중" 판정은 클라이언트가 합니다(STG-007). */
public enum CongestionLevel {
    SMOOTH, NORMAL, CROWDED, VERY_CROWDED
}
