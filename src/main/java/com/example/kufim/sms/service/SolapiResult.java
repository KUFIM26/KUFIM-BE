package com.example.kufim.sms.service;

import com.example.kufim.sms.entity.SmsStatus;

/**
 * 문자 발송 한 건의 결과. 이력(sms_send_log)에 그대로 옮겨 적습니다.
 * 제공자가 접수를 확인(ack)해 준 경우에만 SENT로 적고, 그 밖에는 사실대로 남깁니다.
 */
public record SolapiResult(SmsStatus status, String providerId, String errorCode, Integer cost) {

    public static SolapiResult sent(String providerId, Integer cost) {
        return new SolapiResult(SmsStatus.SENT, providerId, null, cost);
    }

    public static SolapiResult failed(String errorCode, String providerId) {
        return new SolapiResult(SmsStatus.FAILED, providerId, truncate(errorCode), null);
    }

    public static SolapiResult notConfigured() {
        return new SolapiResult(SmsStatus.NOT_CONFIGURED, null, "PROVIDER_NOT_CONFIGURED", null);
    }

    public static SolapiResult skippedLimit() {
        return new SolapiResult(SmsStatus.SKIPPED_LIMIT, null, "DAILY_LIMIT_REACHED", null);
    }

    public static SolapiResult skippedNoNumber() {
        return new SolapiResult(SmsStatus.SKIPPED_LIMIT, null, "NO_PHONE_NUMBER", null);
    }

    /** error_code 컬럼이 40자라 넘치면 자릅니다. */
    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 40 ? value : value.substring(0, 40);
    }
}
