package com.trueyield.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configures the Gemini API HTTP client and retry behaviour.
 *
 * <p>The API key is read exclusively from the {@code GEMINI_API_KEY} environment variable.
 * It is NEVER logged or hardcoded.
 *
 * <p>Retry configuration (exponential backoff on HTTP 503):
 * <pre>
 * gemini:
 *   api:
 *     retry:
 *       max-attempts: 3
 *       initial-delay-ms: 1000
 * </pre>
 */
@Configuration
public class GeminiConfig {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.base-url}")
    private String baseUrl;

    @Value("${gemini.api.model}")
    private String model;

    @Value("${gemini.api.timeout-seconds:30}")
    private int timeoutSeconds;

    /** Maximum number of Gemini call attempts (including the first). */
    @Value("${gemini.api.retry.max-attempts:3}")
    private int maxRetryAttempts;

    /**
     * Initial backoff delay in milliseconds before the first retry.
     * Subsequent retries use exponential backoff with jitter.
     */
    @Value("${gemini.api.retry.initial-delay-ms:1000}")
    private long initialDelayMs;

    public String getApiKey()          { return apiKey; }
    public String getBaseUrl()         { return baseUrl; }
    public String getModel()           { return model; }
    public Duration getTimeout()       { return Duration.ofSeconds(timeoutSeconds); }
    public int getMaxRetryAttempts()   { return maxRetryAttempts; }
    public long getInitialDelayMs()    { return initialDelayMs; }

    @Bean
    public RestClient geminiRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
