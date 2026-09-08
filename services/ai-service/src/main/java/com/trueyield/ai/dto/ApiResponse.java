package com.trueyield.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Standardized API envelope returned by all endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        LocalDateTime timestamp
) {
    /** Successful response with data. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, null, data, LocalDateTime.now());
    }

    /** Error response (no data). */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null, LocalDateTime.now());
    }
}
