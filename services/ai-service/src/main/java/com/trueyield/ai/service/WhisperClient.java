package com.trueyield.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trueyield.ai.config.WhisperConfig;
import com.trueyield.ai.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP client that calls the local faster-whisper Python service.
 *
 * <p>Endpoint: {@code POST /transcribe}  (multipart/form-data, field "audio")
 * <p>Response:  {@code {"text": "..."}} on success
 *
 * <p>Error differentiation:
 * <ul>
 *   <li>Service unreachable → {@code AI_SERVICE_UNAVAILABLE}
 *   <li>HTTP 4xx/5xx from service → {@code TRANSCRIPTION_FAILED}
 *   <li>Empty transcript → {@code TRANSCRIPTION_FAILED}
 * </ul>
 */
@Service
public class WhisperClient {

    private static final Logger log = LoggerFactory.getLogger(WhisperClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WhisperClient(
            @Qualifier("whisperRestClient") RestClient whisperRestClient,
            ObjectMapper objectMapper
    ) {
        this.restClient   = whisperRestClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Send WAV audio bytes to the local Whisper service and return the transcript.
     *
     * @param wavBytes 16 kHz mono PCM WAV bytes
     * @param filename original filename (used for logging only)
     * @return transcript string from Whisper (never null or blank)
     * @throws AiServiceException if the Whisper service is unreachable or transcription fails
     */
    public String transcribe(byte[] wavBytes, String filename) {
        log.info("Whisper transcription started — sending {} bytes", wavBytes.length);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("audio", new NamedByteArrayResource(wavBytes, "audio.wav"));

        String responseJson;
        try {
            responseJson = restClient.post()
                    .uri("/transcribe")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

        } catch (ResourceAccessException ex) {
            // Connection refused / service not running
            log.error("Whisper service is unreachable: {}", ex.getMessage());
            throw new AiServiceException(
                    "AI_SERVICE_UNAVAILABLE",
                    "The speech-to-text service is currently unavailable. " +
                    "Please ensure the Whisper service is running at " + getServiceUrl() + ".",
                    ex
            );
        } catch (RestClientResponseException ex) {
            log.error("Whisper service returned HTTP {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AiServiceException(
                    "TRANSCRIPTION_FAILED",
                    "Speech transcription failed. Please try recording again.",
                    ex
            );
        }

        return parseTranscript(responseJson);
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private String parseTranscript(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            log.error("Whisper service returned an empty response body");
            throw new AiServiceException(
                    "TRANSCRIPTION_FAILED",
                    "The speech-to-text service returned an empty response."
            );
        }

        try {
            JsonNode root = objectMapper.readTree(responseJson);

            // Check for error fields returned by the Python service
            if (root.has("error")) {
                String errorCode   = root.path("error").asText();
                String errorDetail = root.path("detail").asText("Transcription failed");
                log.error("Whisper service error [{}]: {}", errorCode, errorDetail);
                throw new AiServiceException(
                        "TRANSCRIPTION_FAILED",
                        "Speech transcription failed: " + errorDetail
                );
            }

            String text = root.path("text").asText("").trim();
            if (text.isBlank()) {
                log.warn("Whisper service returned an empty transcript — no speech detected");
                throw new AiServiceException(
                        "TRANSCRIPTION_FAILED",
                        "No speech was detected in the recording. Please speak clearly and try again."
                );
            }

            log.info("Whisper transcription completed — transcript length: {} chars", text.length());
            return text;

        } catch (AiServiceException ex) {
            throw ex; // already wrapped
        } catch (Exception ex) {
            log.error("Failed to parse Whisper response: {}", ex.getMessage());
            throw new AiServiceException(
                    "TRANSCRIPTION_FAILED",
                    "Could not process the speech-to-text response. Please try again.",
                    ex
            );
        }
    }

    private String getServiceUrl() {
        // Used only in error messages — avoids importing WhisperConfig just for logging
        return "http://localhost:9000";
    }

    // ─── inner resource class ─────────────────────────────────────────────────

    /**
     * ByteArrayResource that also carries a filename — required by Spring's multipart encoder
     * to include the {@code Content-Disposition: form-data; name="audio"; filename="..."} header.
     */
    static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
