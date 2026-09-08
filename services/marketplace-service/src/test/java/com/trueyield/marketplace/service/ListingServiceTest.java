package com.trueyield.marketplace.service;

import com.trueyield.marketplace.dto.CreateListingRequest;
import com.trueyield.marketplace.dto.ListingResponse;
import com.trueyield.marketplace.entity.Listing;
import com.trueyield.marketplace.enums.*;
import com.trueyield.marketplace.exception.ListingNotFoundException;
import com.trueyield.marketplace.mapper.ListingMapper;
import com.trueyield.marketplace.repository.ListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for ListingService.
 * Repository is mocked — no database required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ListingService")
class ListingServiceTest {

    @Mock
    private ListingRepository listingRepository;

    private ListingService service;

    @BeforeEach
    void setUp() {
        service = new ListingService(listingRepository, new ListingMapper());
    }

    @Test
    @DisplayName("should create listing successfully for a valid request")
    void shouldCreateListingSuccessfully() {
        CreateListingRequest request = buildValidRequest();
        Listing savedListing = buildSavedListing();

        given(listingRepository.save(any(Listing.class))).willReturn(savedListing);

        ListingResponse response = service.createListing(request);

        assertThat(response).isNotNull();
        assertThat(response.product()).isEqualTo(Product.RAMBUTAN);
        assertThat(response.quantity()).isEqualByComparingTo("50");
        assertThat(response.pricePerUnit()).isEqualByComparingTo("450");
        assertThat(response.status()).isEqualTo(ListingStatus.AVAILABLE);
        assertThat(response.estimatedValue()).isEqualByComparingTo("22500");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when quantity is zero")
    void shouldFailWhenQuantityIsZero() {
        CreateListingRequest request = new CreateListingRequest(
                Product.RAMBUTAN, BigDecimal.ZERO, Unit.KG, Quality.GOOD, new BigDecimal("450")
        );

        assertThatThrownBy(() -> service.createListing(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be greater than zero");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when price is zero")
    void shouldFailWhenPriceIsZero() {
        CreateListingRequest request = new CreateListingRequest(
                Product.RAMBUTAN, new BigDecimal("50"), Unit.KG, Quality.GOOD, BigDecimal.ZERO
        );

        assertThatThrownBy(() -> service.createListing(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price per kilogram must be greater than zero");
    }

    @Test
    @DisplayName("should retrieve all listings ordered by createdAt desc")
    void shouldReturnAllListings() {
        Listing listing = buildSavedListing();
        given(listingRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(listing));

        List<ListingResponse> result = service.getAllListings();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).product()).isEqualTo(Product.RAMBUTAN);
    }

    @Test
    @DisplayName("should return listing by ID when found")
    void shouldReturnListingById() {
        Listing listing = buildSavedListing();
        given(listingRepository.findById(listing.getId())).willReturn(Optional.of(listing));

        ListingResponse response = service.getListingById(listing.getId());

        assertThat(response.id()).isEqualTo(listing.getId());
    }

    @Test
    @DisplayName("should throw ListingNotFoundException when listing ID does not exist")
    void shouldThrowNotFoundForUnknownId() {
        UUID unknownId = UUID.randomUUID();
        given(listingRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getListingById(unknownId))
                .isInstanceOf(ListingNotFoundException.class);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private CreateListingRequest buildValidRequest() {
        return new CreateListingRequest(
                Product.RAMBUTAN,
                new BigDecimal("50"),
                Unit.KG,
                Quality.GOOD,
                new BigDecimal("450")
        );
    }

    private Listing buildSavedListing() {
        Listing l = new Listing();
        l.setId(UUID.randomUUID());
        l.setProduct(Product.RAMBUTAN);
        l.setQuantity(new BigDecimal("50"));
        l.setUnit(Unit.KG);
        l.setQuality(Quality.GOOD);
        l.setPricePerUnit(new BigDecimal("450"));
        l.setStatus(ListingStatus.AVAILABLE);
        l.setCreatedAt(LocalDateTime.now());
        l.setUpdatedAt(LocalDateTime.now());
        return l;
    }
}
