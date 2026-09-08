package com.trueyield.marketplace.controller;

import com.trueyield.marketplace.dto.ApiResponse;
import com.trueyield.marketplace.dto.CreateListingRequest;
import com.trueyield.marketplace.dto.ListingResponse;
import com.trueyield.marketplace.service.ListingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Thin REST controller for marketplace listings.
 * All business logic lives in ListingService.
 *
 * Endpoints:
 *   POST   /api/v1/marketplace/listings
 *   GET    /api/v1/marketplace/listings
 *   GET    /api/v1/marketplace/listings/{id}
 */
@RestController
@RequestMapping("/api/v1/marketplace/listings")
public class ListingController {

    private static final Logger log = LoggerFactory.getLogger(ListingController.class);

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    /**
     * Create a new marketplace listing (called after human confirmation).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ListingResponse>> createListing(
            @Valid @RequestBody CreateListingRequest request
    ) {
        log.info("POST /api/v1/marketplace/listings — product: {}", request.product());
        ListingResponse response = listingService.createListing(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }

    /**
     * Get all listings (most recent first).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ListingResponse>>> getAllListings() {
        log.debug("GET /api/v1/marketplace/listings");
        List<ListingResponse> listings = listingService.getAllListings();
        return ResponseEntity.ok(ApiResponse.ok(listings));
    }

    /**
     * Get a single listing by UUID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ListingResponse>> getListingById(@PathVariable UUID id) {
        log.debug("GET /api/v1/marketplace/listings/{}", id);
        ListingResponse listing = listingService.getListingById(id);
        return ResponseEntity.ok(ApiResponse.ok(listing));
    }
}
