package com.trueyield.ai.exception;

import com.trueyield.ai.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Global exception handler — translates exceptions into standardised JSON error responses.
 *
 * <p>Error code → HTTP status mapping:
 * <ul>
 *   <li>{@code INVALID_AUDIO}            → 400 Bad Request
 *   <li>{@code AUDIO_CONVERSION_FAILED}  → 422 Unprocessable Entity
 *   <li>{@code TRANSCRIPTION_FAILED}     → 422 Unprocessable Entity
 *   <li>{@code AI_SERVICE_UNAVAILABLE}   → 503 Service Unavailable
 *   <li>{@code AI_EXTRACTION_FAILED}     → 502 Bad Gateway
 *   <li>(other {@code AiServiceException} codes) → 503 Service Unavailable
 * </ul>
 *
 * <p>Gemini API errors, Whisper errors, and FFmpeg errors are never exposed verbatim.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiServiceException(AiServiceException ex) {
        HttpStatus status = resolveStatus(ex.getErrorCode());
        if (status.is5xxServerError()) {
            log.error("AI service error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        } else {
            log.warn("AI service error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        }
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ListingValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleListingValidationException(ListingValidationException ex) {
        log.warn("Listing validation failed [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Audio file too large: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(
                        "INVALID_AUDIO",
                        "Audio file is too large. Please keep recordings under 10 MB."
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid request: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error in AI service", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred. Please try again."));
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    /**
     * Map an error code to the appropriate HTTP status.
     *
     * <p>The error code is the primary driver of the HTTP status — not the exception type.
     * This allows different {@link AiServiceException} instances to return different HTTP codes.
     */
    private HttpStatus resolveStatus(String errorCode) {
        if (errorCode == null) return HttpStatus.INTERNAL_SERVER_ERROR;
        return switch (errorCode) {
            case "INVALID_AUDIO"           -> HttpStatus.BAD_REQUEST;               // 400
            case "AUDIO_CONVERSION_FAILED" -> HttpStatus.UNPROCESSABLE_ENTITY;      // 422
            case "TRANSCRIPTION_FAILED"    -> HttpStatus.UNPROCESSABLE_ENTITY;      // 422
            case "AI_EXTRACTION_FAILED"    -> HttpStatus.BAD_GATEWAY;               // 502
            case "AI_SERVICE_UNAVAILABLE"  -> HttpStatus.SERVICE_UNAVAILABLE;       // 503
            default                        -> HttpStatus.SERVICE_UNAVAILABLE;       // 503
        };
    }
}
