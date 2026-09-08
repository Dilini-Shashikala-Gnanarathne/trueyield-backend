package com.trueyield.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trueyield.ai.exception.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for WhisperClient.
 *
 * Uses {@link MockRestServiceServer} to intercept HTTP calls — no real Whisper service is called.
 */
@DisplayName("WhisperClient")
class WhisperClientTest {

    private MockRestServiceServer mockServer;
    private WhisperClient whisperClient;

    private static final byte[] FAKE_WAV = "fake-wav-bytes".getBytes();
    private static final String WHISPER_URL = "http://localhost:9000";

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        RestClient restClient = RestClient.builder()
                .baseUrl(WHISPER_URL)
                .requestFactory(restTemplate.getRequestFactory())
                .build();
        whisperClient = new WhisperClient(restClient, new ObjectMapper());
    }

    // ─── happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("should return transcript on successful response")
    void shouldReturnTranscriptOnSuccess() {
        mockServer.expect(requestTo(WHISPER_URL + "/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"text\": \"I have 50 kilograms of rambutan.\"}",
                        MediaType.APPLICATION_JSON));

        String result = whisperClient.transcribe(FAKE_WAV, "recording.wav");

        assertThat(result).isEqualTo("I have 50 kilograms of rambutan.");
        mockServer.verify();
    }

    @Test
    @DisplayName("should trim whitespace from transcript")
    void shouldTrimTranscript() {
        mockServer.expect(requestTo(WHISPER_URL + "/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"text\": \"  fifty kilograms rambutan  \"}",
                        MediaType.APPLICATION_JSON));

        String result = whisperClient.transcribe(FAKE_WAV, "recording.wav");

        assertThat(result).isEqualTo("fifty kilograms rambutan");
    }

    // ─── HTTP errors ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw TRANSCRIPTION_FAILED on HTTP 500 from Whisper service")
    void shouldThrowTranscriptionFailedOnHttp500() {
        mockServer.expect(requestTo(WHISPER_URL + "/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> whisperClient.transcribe(FAKE_WAV, "recording.wav"))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("TRANSCRIPTION_FAILED"));
    }

    @Test
    @DisplayName("should throw TRANSCRIPTION_FAILED on HTTP 422 from Whisper service")
    void shouldThrowTranscriptionFailedOnHttp422() {
        mockServer.expect(requestTo(WHISPER_URL + "/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"error\":\"EMPTY_TRANSCRIPT\",\"detail\":\"No speech detected.\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> whisperClient.transcribe(FAKE_WAV, "recording.wav"))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("TRANSCRIPTION_FAILED"));
    }

    // ─── empty/bad responses ──────────────────────────────────────────────────

    @Test
    @DisplayName("should throw TRANSCRIPTION_FAILED when response body is empty JSON")
    void shouldThrowWhenResponseBodyIsEmpty() {
        mockServer.expect(requestTo(WHISPER_URL + "/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> whisperClient.transcribe(FAKE_WAV, "recording.wav"))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("TRANSCRIPTION_FAILED"));
    }

    @Test
    @DisplayName("should throw TRANSCRIPTION_FAILED when Whisper returns empty transcript")
    void shouldThrowWhenTranscriptIsEmpty() {
        mockServer.expect(requestTo(WHISPER_URL + "/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"text\": \"\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> whisperClient.transcribe(FAKE_WAV, "recording.wav"))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("TRANSCRIPTION_FAILED"));
    }

    @Test
    @DisplayName("should throw TRANSCRIPTION_FAILED when Whisper returns error field")
    void shouldThrowWhenWhisperReturnsError() {
        mockServer.expect(requestTo(WHISPER_URL + "/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"error\": \"EMPTY_TRANSCRIPT\", \"detail\": \"No speech detected.\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> whisperClient.transcribe(FAKE_WAV, "recording.wav"))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> {
                    AiServiceException aiEx = (AiServiceException) ex;
                    assertThat(aiEx.getErrorCode()).isEqualTo("TRANSCRIPTION_FAILED");
                    assertThat(aiEx.getMessage()).contains("No speech detected");
                });
    }
}
