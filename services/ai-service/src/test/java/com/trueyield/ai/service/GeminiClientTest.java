package com.trueyield.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trueyield.ai.config.GeminiConfig;
import com.trueyield.ai.enums.Intent;
import com.trueyield.ai.enums.Product;
import com.trueyield.ai.enums.Quality;
import com.trueyield.ai.enums.Unit;
import com.trueyield.ai.exception.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for GeminiClient (text-only pipeline, retry logic, error handling).
 *
 * Uses {@link MockRestServiceServer} to intercept HTTP calls — no real Gemini API is called.
 */
@DisplayName("GeminiClient")
class GeminiClientTest {

    private MockRestServiceServer mockServer;
    private GeminiClient geminiClient;

    private static final String TRANSCRIPT = "I have 50 kilograms of rambutan at 450 rupees.";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private static final String VALID_JSON_PART =
            "{\\\"intent\\\":\\\"CREATE_LISTING\\\",\\\"product\\\":\\\"RAMBUTAN\\\"," +
            "\\\"quantity\\\":50,\\\"unit\\\":\\\"KG\\\",\\\"quality\\\":\\\"GOOD\\\"," +
            "\\\"pricePerUnit\\\":450}";

    private static final String VALID_GEMINI_RESPONSE = "{\n" +
            "  \"candidates\": [{\n" +
            "    \"content\": {\n" +
            "      \"parts\": [{\"text\": \"" + VALID_JSON_PART + "\"}]\n" +
            "    }\n" +
            "  }]\n" +
            "}";

    @BeforeEach
    void setUp() {
        GeminiConfig config = mock(GeminiConfig.class);
        given(config.getModel()).willReturn("gemini-3.6-flash");
        given(config.getApiKey()).willReturn("test-key");
        given(config.getMaxRetryAttempts()).willReturn(3);
        given(config.getInitialDelayMs()).willReturn(10L); // very short for tests

        // Build a real RestClient pointed at the mock base URL
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        org.springframework.web.client.RestTemplate restTemplate =
                new org.springframework.web.client.RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        // Adapt RestTemplate into RestClient using the same underlying HttpClient
        RestClient restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
                .build();

        // We need MockRestServiceServer to intercept RestClient calls.
        // RestClient uses the same HttpMessageConverters pipeline.
        // Easiest approach: use a real RestTemplate-backed mock server.
        org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
        mockServer = MockRestServiceServer.bindTo(rt).build();
        RestClient rc = RestClient.create(rt);

        geminiClient = new GeminiClient(rc, config, new ObjectMapper());
    }

    // ─── happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("should return GeminiExtractionResult on successful response")
    void shouldReturnResultOnSuccess() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(VALID_GEMINI_RESPONSE, MediaType.APPLICATION_JSON));

        GeminiClient.GeminiExtractionResult result = geminiClient.extractListingIntent(TRANSCRIPT);

        assertThat(result).isNotNull();
        assertThat(result.transcript()).isEqualTo(TRANSCRIPT);
        assertThat(result.intent().getProduct()).isEqualTo(Product.RAMBUTAN);
        assertThat(result.intent().getIntent()).isEqualTo(Intent.CREATE_LISTING);
        assertThat(result.intent().getQuantity()).isEqualByComparingTo("50");
        assertThat(result.intent().getPricePerUnit()).isEqualByComparingTo("450");
        assertThat(result.intent().getUnit()).isEqualTo(Unit.KG);
        assertThat(result.intent().getQuality()).isEqualTo(Quality.GOOD);
        mockServer.verify();
    }

    // ─── retry on 503 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("should succeed on second attempt after one 503")
    void shouldRetryAndSucceedAfterOne503() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(VALID_GEMINI_RESPONSE, MediaType.APPLICATION_JSON));

        GeminiClient.GeminiExtractionResult result = geminiClient.extractListingIntent(TRANSCRIPT);

        assertThat(result.intent().getProduct()).isEqualTo(Product.RAMBUTAN);
        mockServer.verify();
    }

    @Test
    @DisplayName("should throw AI_SERVICE_UNAVAILABLE after exhausting all retries (3x 503)")
    void shouldThrowAfterExhaustingRetries() {
        for (int i = 0; i < 3; i++) {
            mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }

        assertThatThrownBy(() -> geminiClient.extractListingIntent(TRANSCRIPT))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> {
                    AiServiceException aiEx = (AiServiceException) ex;
                    assertThat(aiEx.getErrorCode()).isEqualTo("AI_SERVICE_UNAVAILABLE");
                    assertThat(aiEx.getMessage()).doesNotContain("voice");
                });
        mockServer.verify();
    }

    @Test
    @DisplayName("should not retry on non-503 HTTP errors")
    void shouldNotRetryOnNon503Error() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> geminiClient.extractListingIntent(TRANSCRIPT))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("AI_SERVICE_UNAVAILABLE"));
        mockServer.verify();
    }

    // ─── bad responses ────────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw AI_EXTRACTION_FAILED when Gemini returns no text content")
    void shouldThrowAiExtractionFailedWhenNoContent() {
        String emptyResponse = "{\"candidates\": [{\"content\": {\"parts\": []}}]}";
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(withSuccess(emptyResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiClient.extractListingIntent(TRANSCRIPT))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("AI_EXTRACTION_FAILED"));
    }

    @Test
    @DisplayName("should throw AI_EXTRACTION_FAILED on malformed JSON from Gemini")
    void shouldThrowOnMalformedJson() {
        String badResponse = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"NOT_VALID_JSON!!!\"}]}}]}";
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(withSuccess(badResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiClient.extractListingIntent(TRANSCRIPT))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("AI_EXTRACTION_FAILED"));
    }

    @Test
    @DisplayName("should skip thought parts and use text parts for extraction")
    void shouldSkipThoughtPartsAndUseTextPart() {
        String responseWithThought = "{\n" +
                "  \"candidates\": [{\n" +
                "    \"content\": {\n" +
                "      \"parts\": [\n" +
                "        {\"thought\": true, \"text\": \"thinking...\"},\n" +
                "        {\"text\": \"" + VALID_JSON_PART + "\"}\n" +
                "      ]\n" +
                "    }\n" +
                "  }]\n" +
                "}";
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("generateContent")))
                .andRespond(withSuccess(responseWithThought, MediaType.APPLICATION_JSON));

        GeminiClient.GeminiExtractionResult result = geminiClient.extractListingIntent(TRANSCRIPT);

        assertThat(result.intent().getProduct()).isEqualTo(Product.RAMBUTAN);
    }
}
