package com.trueyield.ai.exception;

/**
 * Thrown when the Gemini API call fails or returns an unexpected response.
 */
public class AiServiceException extends RuntimeException {

    private final String errorCode;

    public AiServiceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AiServiceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
