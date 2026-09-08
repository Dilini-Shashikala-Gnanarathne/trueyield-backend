package com.trueyield.marketplace.dto;

import com.trueyield.marketplace.enums.ListingStatus;
import com.trueyield.marketplace.enums.Product;
import com.trueyield.marketplace.enums.Quality;
import com.trueyield.marketplace.enums.Unit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for a marketplace listing.
 * This is what the frontend receives — the JPA entity is never exposed directly.
 *
 * estimatedValue is calculated server-side: quantity × pricePerUnit
 */
public record ListingResponse(
        UUID id,
        Product product,
        BigDecimal quantity,
        Unit unit,
        Quality quality,
        BigDecimal pricePerUnit,
        BigDecimal estimatedValue,
        ListingStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
