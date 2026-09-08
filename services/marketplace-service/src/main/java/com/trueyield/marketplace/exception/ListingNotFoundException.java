package com.trueyield.marketplace.exception;

import java.util.UUID;

/**
 * Thrown when a listing is not found by ID.
 */
public class ListingNotFoundException extends RuntimeException {

    private final UUID listingId;

    public ListingNotFoundException(UUID listingId) {
        super("Listing not found: " + listingId);
        this.listingId = listingId;
    }

    public UUID getListingId() {
        return listingId;
    }
}
