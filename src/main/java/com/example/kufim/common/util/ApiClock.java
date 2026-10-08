package com.example.kufim.common.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class ApiClock {
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private ApiClock() {}
    public static LocalDateTime now() { return LocalDateTime.now(KST).truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
    public static OffsetDateTime offset(LocalDateTime value) { return value == null ? null : value.atOffset(KST); }
}
