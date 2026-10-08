package com.example.kufim.common.util;

public final class PersonalDataMasker {
    private PersonalDataMasker() {}

    public static String studentNumber(String value) {
        return value == null || value.length() != 9 ? "***" : value.substring(0, 4) + "*****";
    }

    public static String phoneNumber(String value) {
        return value == null || value.length() != 11 ? "***"
                : value.substring(0, 3) + "****" + value.substring(7);
    }
}
