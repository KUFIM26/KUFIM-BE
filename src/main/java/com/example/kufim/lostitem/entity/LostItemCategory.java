package com.example.kufim.lostitem.entity;

/** 습득물 분류. 신분증·지갑은 사진을 공개하지 않습니다. */
public enum LostItemCategory {
    ELECTRONICS, WALLET_CARD, ID_CARD, CLOTHING, OTHER;

    /** 사진을 방문자에게 공개하면 안 되는 분류인지. */
    public boolean sensitive() {
        return this == ID_CARD || this == WALLET_CARD;
    }
}
