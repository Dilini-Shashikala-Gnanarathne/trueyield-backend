package com.trueyield.marketplace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trueyield.marketplace.dto.CreateListingRequest;
import com.trueyield.marketplace.dto.ListingResponse;
import com.trueyield.marketplace.enums.*;
import com.trueyield.marketplace.exception.GlobalExceptionHandler;
import com.trueyield.marketplace.exception.ListingNotFoundException;
import com.trueyield.marketplace.service.ListingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests using MockMvc (no real HTTP, no database).
 */
@WebMvcTest(ListingController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("ListingController")
class ListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ListingService listingService;

    @Test
    @DisplayName("POST /api/v1/marketplace/listings should return 201 with created listing")
    void shouldReturn201WhenListingCreated() throws Exception {
        ListingResponse mockResponse = buildListingResponse();
        given(listingService.createListing(any(CreateListingRequest.class))).willReturn(mockResponse);

        CreateListingRequest request = new CreateListingRequest(
                Product.RAMBUTAN,
                new BigDecimal("50"),
                Unit.KG,
                Quality.GOOD,
                new BigDecimal("450")
        );

        mockMvc.perform(post("/api/v1/marketplace/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.product").value("RAMBUTAN"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("GET /api/v1/marketplace/listings should return 200 with list")
    void shouldReturn200WithListings() throws Exception {
        given(listingService.getAllListings()).willReturn(List.of(buildListingResponse()));

        mockMvc.perform(get("/api/v1/marketplace/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].product").value("RAMBUTAN"));
    }

    @Test
    @DisplayName("GET /api/v1/marketplace/listings/{id} should return 404 for unknown id")
    void shouldReturn404ForUnknownListingId() throws Exception {
        UUID unknownId = UUID.randomUUID();
        given(listingService.getListingById(unknownId)).willThrow(new ListingNotFoundException(unknownId));

        mockMvc.perform(get("/api/v1/marketplace/listings/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/marketplace/listings should return 400 for null product")
    void shouldReturn400ForNullProduct() throws Exception {
        String badRequest = """
                {
                  "quantity": 50,
                  "unit": "KG",
                  "quality": "GOOD",
                  "pricePerUnit": 450
                }
                """;

        mockMvc.perform(post("/api/v1/marketplace/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private ListingResponse buildListingResponse() {
        return new ListingResponse(
                UUID.randomUUID(),
                Product.RAMBUTAN,
                new BigDecimal("50"),
                Unit.KG,
                Quality.GOOD,
                new BigDecimal("450"),
                new BigDecimal("22500"),
                ListingStatus.AVAILABLE,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
