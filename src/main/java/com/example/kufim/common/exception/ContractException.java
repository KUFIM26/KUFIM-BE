package com.example.kufim.common.exception;

import org.springframework.http.HttpStatus;

public class ContractException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Object details;

    public ContractException(HttpStatus status, String code, String message) { this(status, code, message, null); }
    public ContractException(HttpStatus status, String code, String message, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Object details() { return details; }
}
