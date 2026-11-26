package com.example.kufim.translate.service;

import com.example.kufim.common.exception.ContractException;
import org.springframework.http.HttpStatus;

/**
 * 자동 번역을 쓸 수 없을 때 던집니다.
 *
 * <p>번역은 어디까지나 거들어 주는 기능이라 실패해도 등록 자체는 이어져야 합니다.
 * 프런트는 이 메시지를 영어 칸 아래에 띄우고 직접 입력하도록 안내합니다.
 */
public class TranslationUnavailableException extends ContractException {
    public TranslationUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "T001", "자동 번역을 사용할 수 없습니다. 영어를 직접 입력해 주세요.");
    }
}
