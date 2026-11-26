package com.example.kufim.common.util;

import com.example.kufim.common.exception.ContractException;
import org.springframework.http.HttpStatus;

/**
 * 영어 칸(`…En`)을 다루는 규칙을 한곳에 모읍니다.
 *
 * <p>영어 칸은 세 가지를 구분해야 합니다.
 * <ul>
 *   <li>보내지 않음(null) — 기존 값을 그대로 둡니다.</li>
 *   <li>빈 문자열 — 운영진이 영어를 지웠다는 뜻이라 NULL로 저장합니다.</li>
 *   <li>값 — 앞뒤 공백을 떼고 저장합니다.</li>
 * </ul>
 *
 * <p>프런트는 비우기를 빈 문자열로 보냅니다. 그래서 "안 보냄"과 "지움"이 갈립니다.
 * 한국어 칸은 비울 수 없어 이 구분이 필요 없었습니다.
 */
public final class EnglishText {
    private EnglishText() {}

    /** 등록할 때 쓰는 값. 비어 있으면 저장하지 않습니다. */
    public static String of(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 수정할 때 쓰는 값. 보내지 않았으면(null) 기존 값을 유지합니다. */
    public static String apply(String current, String incoming) {
        return incoming == null ? current : of(incoming);
    }

    /**
     * 길이를 확인합니다. 프런트가 이미 입력칸에서 막고 있지만,
     * 번역 결과가 길어진 채로 올라오는 경우가 있어 서버에서도 거절합니다.
     */
    public static void checkLength(String value, int max, String field) {
        if (value != null && value.trim().length() > max)
            throw new ContractException(HttpStatus.BAD_REQUEST, "C001",
                    "%s의 영어는 %d자까지 입력할 수 있습니다.".formatted(field, max));
    }
}
