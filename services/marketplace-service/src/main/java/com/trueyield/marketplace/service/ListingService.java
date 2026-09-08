package com.trueyield.marketplace.service;

import com.trueyield.marketplace.dto.CreateListingRequest;
import com.trueyield.marketplace.dto.ListingResponse;
import com.trueyield.marketplace.entity.Listing;
import com.trueyield.marketplace.enums.ListingStatus;
import com.trueyield.marketplace.enums.Product;
import com.trueyield.marketplace.enums.Unit;
import com.trueyield.marketplace.exception.ListingNotFoundException;
import com.trueyield.marketplace.mapper.ListingMapper;
import com.trueyield.marketplace.repository.ListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for marketplace listings.
 *
 * Architectural rules:
 * - Re-validates ALL input (never trusts the AI Service or frontend)
 * - Calculates derived values (e.g., estimatedValue) server-side
 * - Controllers are thin; this class owns all business decisions
 * - Never exposes JPA entities to callers; always returns DTOs
 */
@Service
@Transactional(readOnly = true)
public class ListingService {

    private static final Logger log = LoggerFactory.getLogger(ListingService.class);

    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;

    public ListingService(ListingRepository listingRepository, ListingMapper listingMapper) {
        this.listingRepository = listingRepository;
        this.listingMapper = listingMapper;
    }

    /**
     * Create a new marketplace listing.
     *
     * Business rules enforced here (second validation layer):
     * - Product must be RAMBUTAN
     * - Unit must be KG
     * - Quantity must be > 0
     * - Price per unit must be > 0
     * - New listings always start as AVAILABLE
     *
     * @param request validated request DTO
     * @return persisted listing as response DTO
     */
    @Transactional
    public ListingResponse createListing(CreateListingRequest request) {
        log.info("Creating listing — product: {}, quantity: {} {}, price: {}/KG",
                request.product(), request.quantity(), request.unit(), request.pricePerUnit());

        // Second validation layer — never rely solely on Bean Validation
        validateListingRequest(request);

        Listing listing = new Listing();
        listing.setProduct(request.product());
        listing.setQuantity(request.quantity());
        listing.setUnit(request.unit());
        listing.setQuality(request.quality());
        listing.setPricePerUnit(request.pricePerUnit());
        listing.setStatus(ListingStatus.AVAILABLE);

        Listing saved = listingRepository.save(listing);
        log.info("Listing created — id: {}, status: AVAILABLE", saved.getId());

        return listingMapper.toResponse(saved);
    }

    /**
     * Retrieve all listings, ordered by creation date descending.
     */
    public List<ListingResponse> getAllListings() {
        log.debug("Retrieving all listings");
        return listingMapper.toResponseList(listingRepository.findAllByOrderByCreatedAtDesc());
    }

    /**
     * Retrieve a single listing by ID.
     *
     * @throws ListingNotFoundException if no listing exists with the given ID
     */
    public ListingResponse getListingById(UUID id) {
        log.debug("Retrieving listing by id: {}", id);
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ListingNotFoundException(id));
        return listingMapper.toResponse(listing);
    }

    // ─── private validation (second layer) ───────────────────────────────────

    private void validateListingRequest(CreateListingRequest request) {
        if (request.product() != Product.RAMBUTAN) {
            throw new IllegalArgumentException(
                    "Unsupported product: " + request.product() + ". Currently only RAMBUTAN is supported."
            );
        }
        if (request.unit() != Unit.KG) {
            throw new IllegalArgumentException(
                    "Unsupported unit: " + request.unit() + ". Currently only KG is supported."
            );
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        if (request.pricePerUnit() == null || request.pricePerUnit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price per kilogram must be greater than zero.");
        }
    }
}
