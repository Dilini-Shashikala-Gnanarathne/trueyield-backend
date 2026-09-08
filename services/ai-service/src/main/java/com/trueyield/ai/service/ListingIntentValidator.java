package com.trueyield.ai.service;

import com.trueyield.ai.dto.ListingIntentResponse;
import com.trueyield.ai.enums.Intent;
import com.trueyield.ai.enums.Product;
import com.trueyield.ai.enums.Unit;
import com.trueyield.ai.exception.ListingValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Validates the listing intent extracted by AI (Gemini).
 *
 * AI output is treated as UNTRUSTED input. Every field is checked
 * independently before the response is forwarded to the frontend.
 * A ListingValidationException is thrown for any constraint violation.
 */
@Component
public class ListingIntentValidator {

    private static final Logger log = LoggerFactory.getLogger(ListingIntentValidator.class);

    /**
     * Validate a Gemini-extracted listing intent.
     *
     * @param response the DTO extracted from Gemini
     * @throws ListingValidationException if any field is missing or invalid
     */
    public void validate(ListingIntentResponse response) {
        log.debug("Validating AI-extracted listing intent: {}", response);

        if (response == null) {
            throw new ListingValidationException(
                    "INVALID_LISTING_DATA",
                    "Could not extract any listing information from your voice. Please try speaking more clearly."
            );
        }

        validateIntent(response.getIntent());
        validateProduct(response.getProduct());
        validateUnit(response.getUnit());
        validateQuantity(response.getQuantity());
        validatePrice(response.getPricePerUnit());
        // Quality defaults to UNKNOWN if null — allowed

        log.debug("AI-extracted listing intent passed validation");
    }

    private void validateIntent(Intent intent) {
        if (intent == null || intent == Intent.UNKNOWN) {
            throw new ListingValidationException(
                    "INVALID_LISTING_DATA",
                    "We could not understand your intent. Please say something like: 'I have 50 kilograms of rambutan at 450 rupees per kilogram.'"
            );
        }
    }

    private void validateProduct(Product product) {
        if (product == null) {
            throw new ListingValidationException(
                    "INVALID_LISTING_DATA",
                    "We could not identify the product. Currently only Rambutan listings are supported."
            );
        }
        if (product != Product.RAMBUTAN) {
            throw new ListingValidationException(
                    "UNSUPPORTED_PRODUCT",
                    "Currently only Rambutan listings are supported. You said: " + product
            );
        }
    }

    private void validateUnit(Unit unit) {
        if (unit == null) {
            throw new ListingValidationException(
                    "INVALID_LISTING_DATA",
                    "We could not identify the unit. Please specify the quantity in kilograms."
            );
        }
        if (unit != Unit.KG) {
            throw new ListingValidationException(
                    "UNSUPPORTED_UNIT",
                    "Currently only kilogram (KG) listings are supported."
            );
        }
    }

    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null) {
            throw new ListingValidationException(
                    "INVALID_LISTING_DATA",
                    "We could not identify the quantity. Please say how many kilograms you have."
            );
        }
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ListingValidationException(
                    "INVALID_LISTING_DATA",
                    "Quantity must be greater than zero."
            );
        }
    }

    private void validatePrice(BigDecimal pricePerUnit) {
        if (pricePerUnit == null) {
            throw new ListingValidationException(
                    "INVALID_LISTING_DATA",
                    "We could not identify the price. Please say the price per kilogram."
            );
        }
        if (pricePerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ListingValidationException(
                    "INVALID_LISTING_DATA",
                    "Price per kilogram must be greater than zero."
            );
        }
    }
}
