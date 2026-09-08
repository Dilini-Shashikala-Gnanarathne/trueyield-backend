package com.trueyield.ai.service;

import com.trueyield.ai.dto.ListingIntentResponse;
import com.trueyield.ai.enums.Intent;
import com.trueyield.ai.enums.Product;
import com.trueyield.ai.enums.Quality;
import com.trueyield.ai.enums.Unit;
import com.trueyield.ai.exception.ListingValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ListingIntentValidator.
 * Verifies all validation rules on AI-extracted listing data.
 */
@DisplayName("ListingIntentValidator")
class ListingIntentValidatorTest {

    private ListingIntentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ListingIntentValidator();
    }

    @Test
    @DisplayName("should pass validation for a complete valid listing intent")
    void shouldPassForValidListing() {
        ListingIntentResponse response = buildValid();
        assertThatNoException().isThrownBy(() -> validator.validate(response));
    }

    @Test
    @DisplayName("should fail when quantity is null")
    void shouldFailWhenQuantityIsNull() {
        ListingIntentResponse response = buildValid();
        response.setQuantity(null);

        assertThatThrownBy(() -> validator.validate(response))
                .isInstanceOf(ListingValidationException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    @DisplayName("should fail when quantity is zero")
    void shouldFailWhenQuantityIsZero() {
        ListingIntentResponse response = buildValid();
        response.setQuantity(BigDecimal.ZERO);

        assertThatThrownBy(() -> validator.validate(response))
                .isInstanceOf(ListingValidationException.class)
                .hasMessageContaining("zero");
    }

    @Test
    @DisplayName("should fail when quantity is negative")
    void shouldFailWhenQuantityIsNegative() {
        ListingIntentResponse response = buildValid();
        response.setQuantity(new BigDecimal("-10"));

        assertThatThrownBy(() -> validator.validate(response))
                .isInstanceOf(ListingValidationException.class);
    }

    @Test
    @DisplayName("should fail when pricePerUnit is null")
    void shouldFailWhenPriceIsNull() {
        ListingIntentResponse response = buildValid();
        response.setPricePerUnit(null);

        assertThatThrownBy(() -> validator.validate(response))
                .isInstanceOf(ListingValidationException.class)
                .hasMessageContaining("price");
    }

    @Test
    @DisplayName("should fail when pricePerUnit is zero")
    void shouldFailWhenPriceIsZero() {
        ListingIntentResponse response = buildValid();
        response.setPricePerUnit(BigDecimal.ZERO);

        assertThatThrownBy(() -> validator.validate(response))
                .isInstanceOf(ListingValidationException.class)
                .hasMessageContaining("zero");
    }

    @Test
    @DisplayName("should fail when product is null")
    void shouldFailWhenProductIsNull() {
        ListingIntentResponse response = buildValid();
        response.setProduct(null);

        assertThatThrownBy(() -> validator.validate(response))
                .isInstanceOf(ListingValidationException.class)
                .hasMessageContaining("product");
    }

    @Test
    @DisplayName("should fail when unit is null")
    void shouldFailWhenUnitIsNull() {
        ListingIntentResponse response = buildValid();
        response.setUnit(null);

        assertThatThrownBy(() -> validator.validate(response))
                .isInstanceOf(ListingValidationException.class)
                .hasMessageContaining("unit");
    }

    @Test
    @DisplayName("should fail when intent is UNKNOWN")
    void shouldFailWhenIntentIsUnknown() {
        ListingIntentResponse response = buildValid();
        response.setIntent(Intent.UNKNOWN);

        assertThatThrownBy(() -> validator.validate(response))
                .isInstanceOf(ListingValidationException.class)
                .hasMessageContaining("intent");
    }

    @Test
    @DisplayName("should pass when quality is null (defaults to UNKNOWN)")
    void shouldPassWhenQualityIsNull() {
        ListingIntentResponse response = buildValid();
        response.setQuality(null);
        // Quality null is handled by VoiceListingService before validation
        // Validator allows null quality by design — UNKNOWN is assigned upstream
        assertThatNoException().isThrownBy(() -> validator.validate(response));
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private ListingIntentResponse buildValid() {
        ListingIntentResponse r = new ListingIntentResponse();
        r.setIntent(Intent.CREATE_LISTING);
        r.setProduct(Product.RAMBUTAN);
        r.setQuantity(new BigDecimal("50"));
        r.setUnit(Unit.KG);
        r.setQuality(Quality.GOOD);
        r.setPricePerUnit(new BigDecimal("450"));
        return r;
    }
}
