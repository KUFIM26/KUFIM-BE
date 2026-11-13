package com.example.kufim.lostitem.entity;

/** 습득물 보관 상태. */
public enum LostItemStatus {
    STORED, RETURNED;

    public String label() {
        return this == STORED ? "보관 중" : "인계 완료";
    }
}
