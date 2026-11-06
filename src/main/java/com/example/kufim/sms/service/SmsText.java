package com.example.kufim.sms.service;

import java.nio.charset.Charset;

/**
 * 문자 본문을 만들 때 쓰는 공통 처리.
 * 문자는 EUC-KR로 변환되어 나가므로 그 표에 없는 글자는 받는 쪽에서 '?'로 깨지고,
 * 단문(90바이트)을 넘으면 장문으로 전환되어 요금이 올라갑니다.
 */
public final class SmsText {
    /** 솔라피 기준 SMS(단문) 한계. */
    public static final int SMS_BYTE_LIMIT = 90;
    public static final Charset EUC_KR = Charset.forName("EUC-KR");

    private SmsText() {}

    /** 보낼 수 없는 글자를 미리 바꿉니다. 줄표류는 모양이 같은 U+2015로, 나머지는 공백으로 둡니다. */
    public static String safe(String value) {
        if (value == null) return "";
        String normalized = value.replace('—', '―').replace('–', '―');
        var encoder = EUC_KR.newEncoder();
        StringBuilder safe = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(code -> {
            String ch = new String(Character.toChars(code));
            safe.append(encoder.canEncode(ch) ? ch : " ");
        });
        return safe.toString().replaceAll(" {2,}", " ").trim();
    }

    public static int bytes(String text) {
        return text == null ? 0 : text.getBytes(EUC_KR).length;
    }

    /** 단문 한도를 넘지 않도록 끝에서부터 줄이고 말줄임표를 붙입니다. */
    public static String fit(String text) {
        if (bytes(text) <= SMS_BYTE_LIMIT) return text;
        String cut = text;
        while (bytes(cut + "…") > SMS_BYTE_LIMIT && cut.length() > 1)
            cut = cut.substring(0, cut.length() - 1);
        return cut.stripTrailing() + "…";
    }
}
