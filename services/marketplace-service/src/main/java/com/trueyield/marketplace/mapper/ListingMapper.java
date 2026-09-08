package com.trueyield.marketplace.mapper;

import com.trueyield.marketplace.dto.ListingResponse;
import com.trueyield.marketplace.entity.Listing;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps between Listing JPA entities and ListingResponse DTOs.
 * estimatedValue (quantity × pricePerUnit) is always calculated here — never accepted from clients.
 */
@Component
public class ListingMapper {

    public ListingResponse toResponse(Listing listing) {
        return new ListingResponse(
                listing.getId(),
                listing.getProduct(),
                listing.getQuantity(),
                listing.getUnit(),
                listing.getQuality(),
                listing.getPricePerUnit(),
                listing.getQuantity().multiply(listing.getPricePerUnit()), // server-calculated
                listing.getStatus(),
                listing.getCreatedAt(),
                listing.getUpdatedAt()
        );
    }

    public List<ListingResponse> toResponseList(List<Listing> listings) {
        return listings.stream()
                .map(this::toResponse)
                .toList();
    }
}
