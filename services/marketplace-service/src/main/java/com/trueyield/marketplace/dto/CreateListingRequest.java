package com.trueyield.marketplace.dto;

import com.trueyield.marketplace.enums.Product;
import com.trueyield.marketplace.enums.Quality;
import com.trueyield.marketplace.enums.Unit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new marketplace listing.
 *
 * Bean Validation constraints enforce the second layer of validation
 * (the AI Service validates first; Marketplace Service validates again).
 *
 * Totals (e.g., estimatedValue) are NEVER accepted from the frontend —
 * they are calculated server-side only.
 */
public record CreateListingRequest(

        @NotNull(message = "Product is required")
        Product product,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.01", message = "Quantity must be greater than zero")
        BigDecimal quantity,

        @NotNull(message = "Unit is required")
        Unit unit,

        @NotNull(message = "Quality is required")
        Quality quality,

        @NotNull(message = "Price per unit is required")
        @DecimalMin(value = "0.01", message = "Price per kilogram must be greater than zero")
        BigDecimal pricePerUnit
) {}
