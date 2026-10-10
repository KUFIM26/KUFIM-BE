package com.example.kufim.common.util;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** 사후 분석용 CSV. Excel에서 한글이 깨지지 않도록 UTF-8 BOM을 붙입니다(ADM-010). */
public final class Csv {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final StringBuilder out = new StringBuilder("﻿");

    /** 헤더는 영문 식별자라 따옴표 없이 씁니다. */
    public Csv(String... header) { out.append(String.join(",", header)).append("\r\n"); }

    public Csv row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) out.append(',');
            out.append(cell(cells[i]));
        }
        out.append("\r\n");
        return this;
    }

    public ResponseEntity<byte[]> download(String filename) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .body(out.toString().getBytes(StandardCharsets.UTF_8));
    }

    // 숫자는 그대로, 문자열은 따옴표로 감쌉니다. =,+,-,@로 시작하면 Excel 수식으로 실행되지 않도록 앞에 '를 붙입니다.
    private static String cell(Object value) {
        if (value == null) return "";
        if (value instanceof Number || value instanceof Enum<?>) return value.toString();
        if (value instanceof LocalDateTime time) return TIME.format(time);
        String v = value.toString();
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
