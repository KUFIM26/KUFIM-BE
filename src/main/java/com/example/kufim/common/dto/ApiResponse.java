package com.example.kufim.common.dto;

public record ApiResponse<T>(boolean success, T data, ErrorBody error) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, data, null); }
    public static ApiResponse<Void> fail(String code, String message, String field, Object details, Object violations) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, field, details, violations));
    }
    public record ErrorBody(String code, String message, String field, Object details, Object violations) {}
}
