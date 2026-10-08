package com.example.kufim.waiting.service;

import com.example.kufim.common.exception.ContractException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ClientToken {
    private ClientToken() {}
    public static String require(String raw) {
        try {
            UUID value = UUID.fromString(raw);
            if (!value.toString().equals(raw) || value.version() != 4) throw new IllegalArgumentException();
            return raw;
        } catch (Exception exception) {
            throw new ContractException(HttpStatus.BAD_REQUEST, "C003", "유효한 이용자 토큰이 필요합니다.");
        }
    }
}
