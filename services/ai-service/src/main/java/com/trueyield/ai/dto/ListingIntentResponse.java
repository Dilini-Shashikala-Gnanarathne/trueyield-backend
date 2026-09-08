package com.trueyield.ai.dto;

import com.trueyield.ai.enums.Intent;
import com.trueyield.ai.enums.Product;
import com.trueyield.ai.enums.Quality;
import com.trueyield.ai.enums.Unit;

import java.math.BigDecimal;

/**
 * Represents the structured intent extracted by Gemini from a farmer's voice recording.
 * This is UNTRUSTED data — always validate before using downstream.
 */
public class ListingIntentResponse {

    private Intent intent;
    private Product product;
    private BigDecimal quantity;
    private Unit unit;
    private Quality quality;
    private BigDecimal pricePerUnit;

    public ListingIntentResponse() {}

    public ListingIntentResponse(Intent intent, Product product, BigDecimal quantity,
                                  Unit unit, Quality quality, BigDecimal pricePerUnit) {
        this.intent = intent;
        this.product = product;
        this.quantity = quantity;
        this.unit = unit;
        this.quality = quality;
        this.pricePerUnit = pricePerUnit;
    }

    public Intent getIntent() { return intent; }
    public void setIntent(Intent intent) { this.intent = intent; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }

    public Quality getQuality() { return quality; }
    public void setQuality(Quality quality) { this.quality = quality; }

    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }

    @Override
    public String toString() {
        return "ListingIntentResponse{" +
                "intent=" + intent +
                ", product=" + product +
                ", quantity=" + quantity +
                ", unit=" + unit +
                ", quality=" + quality +
                ", pricePerUnit=" + pricePerUnit +
                '}';
    }
}
