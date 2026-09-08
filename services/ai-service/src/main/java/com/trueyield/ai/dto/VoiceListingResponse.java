package com.trueyield.ai.dto;

/**
 * Top-level response returned to the frontend after voice processing.
 * Contains the raw transcript and the validated, structured listing intent.
 */
public record VoiceListingResponse(
        String transcript,
        ListingIntentResponse listing
) {}
