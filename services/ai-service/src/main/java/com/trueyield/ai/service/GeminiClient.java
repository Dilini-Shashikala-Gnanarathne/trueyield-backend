package com.trueyield.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trueyield.ai.config.GeminiConfig;
import com.trueyield.ai.dto.ListingIntentResponse;
import com.trueyield.ai.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * HTTP client for the Google Gemini Generative Language API.
 *
 * <h2>Key responsibilities</h2>
 * <ul>
 *   <li>Accept a plain-text transcript from Whisper STT (no audio bytes)
 *   <li>Build a text-only Gemini request with a structured extraction prompt
 *   <li>Parse the JSON response into a {@link ListingIntentResponse}
 *   <li>Retry on HTTP 503 with exponential backoff + jitter (configurable)
 * </ul>
 *
 * <h2>Error codes thrown</h2>
 * <ul>
 *   <li>{@code AI_SERVICE_UNAVAILABLE} — Gemini returned 503 after all retries exhausted
 *   <li>{@code AI_EXTRACTION_FAILED}   — Gemini returned bad/unparseable JSON
 * </ul>
 *
 * <p>This class NEVER receives audio bytes and NEVER touches the database.
 */
@Service
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    /**
     * Text-only extraction prompt.
     * Gemini receives the Whisper transcript and returns a structured JSON object.
     * No audio data is sent.
     */
    private static final String EXTRACTION_PROMPT_TEMPLATE = """
            You are an agricultural marketplace intent extraction assistant.

            A farmer has dictated the following voice message (already transcribed):

            Transcript:
            "%s"

            Extract structured listing information from the transcript above.

            Supported product: RAMBUTAN
            Supported units: KG
            Supported qualities: GOOD, AVERAGE, PREMIUM, POOR, UNKNOWN
            Supported intents: CREATE_LISTING, UNKNOWN

            Rules:
            1. Never invent missing values.
            2. Return null for any value not clearly stated by the farmer.
            3. Do not calculate values not provided.
            4. Do not create database records — you only extract information.
            5. Return ONLY valid JSON — no markdown, no explanation, no code fences.
            6. Normalize product name to RAMBUTAN.
            7. Normalize quantity to a positive number.
            8. Normalize pricePerUnit to a positive number.
            9. Normalize unit to KG.
            10. Normalize quality to one of: GOOD, AVERAGE, PREMIUM, POOR, UNKNOWN.

            Return exactly this JSON structure:
            {
              "intent": "CREATE_LISTING or UNKNOWN",
              "product": "RAMBUTAN or null",
              "quantity": <number or null>,
              "unit": "KG or null",
              "quality": "GOOD or AVERAGE or PREMIUM or POOR or UNKNOWN or null",
              "pricePerUnit": <number or null>
            }
            """;

    private final RestClient restClient;
    private final GeminiConfig config;
    private final ObjectMapper objectMapper;

    public GeminiClient(RestClient geminiRestClient, GeminiConfig config, ObjectMapper objectMapper) {
        this.restClient   = geminiRestClient;
        this.config       = config;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Send a Whisper transcript to Gemini and extract structured listing information.
     *
     * <p>Audio is NEVER sent here. Only the text transcript is transmitted.
     *
     * @param transcript plain-text transcript returned by Whisper
     * @return structured listing intent extracted from the transcript
     * @throws AiServiceException with code {@code AI_SERVICE_UNAVAILABLE} if Gemini returns 503
     *                            after all retries, or {@code AI_EXTRACTION_FAILED} on bad JSON
     */
    public GeminiExtractionResult extractListingIntent(String transcript) {
        log.info("Gemini text extraction started — transcript length: {} chars", transcript.length());

        String prompt = String.format(EXTRACTION_PROMPT_TEMPLATE, sanitiseTranscript(transcript));
        Map<String, Object> requestBody = buildTextOnlyRequest(prompt);

        String responseJson = callWithRetry(requestBody);

        log.info("Gemini extraction completed");
        return parseGeminiResponse(responseJson, transcript);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Retry logic — exponential backoff with jitter
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call Gemini with exponential backoff on HTTP 503.
     *
     * <p>Retry sequence (with default config: 3 attempts, 1000 ms initial delay):
     * <pre>
     *   Attempt 1 → success or non-503 error: return immediately
     *   Attempt 1 → 503: wait ~1000 ms ± jitter
     *   Attempt 2 → 503: wait ~2000 ms ± jitter
     *   Attempt 3 → 503: throw AI_SERVICE_UNAVAILABLE
     * </pre>
     */
    private String callWithRetry(Map<String, Object> requestBody) {
        int   maxAttempts    = config.getMaxRetryAttempts();
        long  initialDelayMs = config.getInitialDelayMs();
        int   attempt        = 0;

        while (true) {
            attempt++;
            log.debug("Gemini API call — attempt {}/{}", attempt, maxAttempts);

            try {
                return restClient
                        .post()
                        .uri("/models/{model}:generateContent?key={key}", config.getModel(), config.getApiKey())
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();

                if (status == 503 && attempt < maxAttempts) {
                    long delayMs = computeBackoff(initialDelayMs, attempt);
                    log.warn("Gemini returned 503 (attempt {}/{}). Retrying in {} ms…",
                            attempt, maxAttempts, delayMs);
                    sleepSilently(delayMs);
                    continue;
                }

                if (status == 503) {
                    log.error("Gemini returned 503 after {} attempt(s). Giving up.", attempt);
                    throw new AiServiceException(
                            "AI_SERVICE_UNAVAILABLE",
                            "The AI extraction service is temporarily unavailable due to high demand. " +
                            "Please try again in a moment.",
                            ex
                    );
                }

                // Any other HTTP error (400, 401, 429, 500, …)
                log.error("Gemini API returned HTTP {}: {}", status, ex.getMessage());
                throw new AiServiceException(
                        "AI_SERVICE_UNAVAILABLE",
                        "The AI extraction service returned an unexpected error. Please try again.",
                        ex
                );

            } catch (ResourceAccessException ex) {
                // Connection refused / DNS failure
                log.error("Cannot reach Gemini API: {}", ex.getMessage());
                throw new AiServiceException(
                        "AI_SERVICE_UNAVAILABLE",
                        "Cannot connect to the AI extraction service. Please check your internet connection.",
                        ex
                );
            }
        }
    }

    /**
     * Compute exponential backoff with ±25% random jitter.
     *
     * @param initialMs base delay in ms
     * @param attempt   current attempt number (1-based; first retry is attempt=1)
     * @return milliseconds to sleep
     */
    private long computeBackoff(long initialMs, int attempt) {
        long exponential = initialMs * (1L << (attempt - 1)); // 1s, 2s, 4s, …
        long jitter = ThreadLocalRandom.current().nextLong(
                -(exponential / 4), (exponential / 4) + 1
        );
        return Math.max(100, exponential + jitter);
    }

    private void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Request building
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildTextOnlyRequest(String prompt) {
        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> content  = Map.of("parts", List.of(textPart));

        Map<String, Object> generationConfig = Map.of(
                "responseMimeType", "application/json",
                "temperature", 0.0
        );

        return Map.of(
                "contents",         List.of(content),
                "generationConfig", generationConfig
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Response parsing
    // ─────────────────────────────────────────────────────────────────────────

    private GeminiExtractionResult parseGeminiResponse(String responseJson, String originalTranscript) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);

            // Navigate candidates[0].content.parts[], skip thought parts
            JsonNode partsNode = root.path("candidates").path(0).path("content").path("parts");
            String extractedJson = null;

            if (partsNode.isArray()) {
                for (JsonNode part : partsNode) {
                    if (part.has("thought") && part.get("thought").asBoolean()) {
                        continue; // skip Gemini thinking traces
                    }
                    if (part.has("text") && !part.get("text").asText().isBlank()) {
                        extractedJson = part.get("text").asText().trim();
                        break;
                    }
                }
            }

            if (extractedJson == null) {
                log.error("Gemini returned no valid text content. Raw response (truncated): {}",
                        responseJson.length() > 300 ? responseJson.substring(0, 300) + "…" : responseJson);
                throw new AiServiceException(
                        "AI_EXTRACTION_FAILED",
                        "The AI could not extract listing information from your description. Please try again."
                );
            }

            // Strip markdown code fences if present (defensive, since we request application/json)
            if (extractedJson.startsWith("```")) {
                extractedJson = extractedJson
                        .replaceAll("^```(?:json)?\\s*", "")
                        .replaceAll("\\s*```$", "")
                        .trim();
            }

            ListingIntentResponse intent = objectMapper.readValue(extractedJson, ListingIntentResponse.class);

            log.debug("Gemini extraction result: {}", intent);
            return new GeminiExtractionResult(originalTranscript, intent);

        } catch (AiServiceException ex) {
            throw ex; // already wrapped
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse Gemini response JSON: {}", ex.getMessage());
            throw new AiServiceException(
                    "AI_EXTRACTION_FAILED",
                    "The AI returned an unexpected response format. Please try again.",
                    ex
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Prevent prompt injection by escaping double-quotes in the transcript before
     * embedding it into the prompt template.
     */
    private String sanitiseTranscript(String transcript) {
        return transcript.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Result record
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Internal result wrapper — original Whisper transcript + parsed intent from Gemini.
     */
    public record GeminiExtractionResult(String transcript, ListingIntentResponse intent) {}
}
