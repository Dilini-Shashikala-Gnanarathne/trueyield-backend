package com.trueyield.marketplace.repository;

import com.trueyield.marketplace.entity.Listing;
import com.trueyield.marketplace.enums.ListingStatus;
import com.trueyield.marketplace.enums.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for Listing entities.
 * Business logic belongs in ListingService — not here.
 */
@Repository
public interface ListingRepository extends JpaRepository<Listing, UUID> {

    List<Listing> findAllByOrderByCreatedAtDesc();

    List<Listing> findByProduct(Product product);

    List<Listing> findByStatus(ListingStatus status);
}
