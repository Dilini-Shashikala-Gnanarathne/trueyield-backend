package com.trueyield.ai.exception;

/**
 * Thrown when the AI-extracted listing data fails business validation.
 */
public class ListingValidationException extends RuntimeException {

    private final String errorCode;

    public ListingValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
