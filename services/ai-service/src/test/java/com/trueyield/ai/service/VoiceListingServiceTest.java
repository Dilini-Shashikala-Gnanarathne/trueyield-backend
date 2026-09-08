package com.trueyield.ai.service;

import com.trueyield.ai.dto.ListingIntentResponse;
import com.trueyield.ai.dto.VoiceListingResponse;
import com.trueyield.ai.enums.Intent;
import com.trueyield.ai.enums.Product;
import com.trueyield.ai.enums.Quality;
import com.trueyield.ai.enums.Unit;
import com.trueyield.ai.exception.AiServiceException;
import com.trueyield.ai.exception.ListingValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for VoiceListingService.
 *
 * AudioConverter, WhisperClient, and GeminiClient are all mocked —
 * no FFmpeg, no Whisper service, no Gemini API calls are made.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VoiceListingService")
class VoiceListingServiceTest {

    @Mock
    private AudioConverter audioConverter;

    @Mock
    private WhisperClient whisperClient;

    @Mock
    private GeminiClient geminiClient;

    private VoiceListingService service;

    private static final byte[] FAKE_WAV = "fake-wav-bytes".getBytes();
    private static final String SAMPLE_TRANSCRIPT =
            "I have 50 kilograms of good rambutan at 450 rupees per kilogram.";

    @BeforeEach
    void setUp() {
        service = new VoiceListingService(audioConverter, whisperClient, geminiClient, new ListingIntentValidator());
    }

    // ─── happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("should return VoiceListingResponse when full pipeline succeeds")
    void shouldReturnResponseOnSuccess() {
        ListingIntentResponse intent = buildValidIntent();
        given(audioConverter.convertToWav(any())).willReturn(FAKE_WAV);
        given(whisperClient.transcribe(any(byte[].class), anyString())).willReturn(SAMPLE_TRANSCRIPT);
        given(geminiClient.extractListingIntent(anyString()))
                .willReturn(new GeminiClient.GeminiExtractionResult(SAMPLE_TRANSCRIPT, intent));

        MockMultipartFile audio = webmFile("recording.webm");

        VoiceListingResponse response = service.processVoiceRecording(audio);

        assertThat(response).isNotNull();
        assertThat(response.transcript()).isEqualTo(SAMPLE_TRANSCRIPT);
        assertThat(response.listing().getProduct()).isEqualTo(Product.RAMBUTAN);
        assertThat(response.listing().getQuantity()).isEqualByComparingTo("50");
        assertThat(response.listing().getPricePerUnit()).isEqualByComparingTo("450");
    }

    @Test
    @DisplayName("should default quality to UNKNOWN when Gemini returns null quality")
    void shouldDefaultQualityToUnknown() {
        ListingIntentResponse intent = buildValidIntent();
        intent.setQuality(null);
        given(audioConverter.convertToWav(any())).willReturn(FAKE_WAV);
        given(whisperClient.transcribe(any(byte[].class), anyString())).willReturn(SAMPLE_TRANSCRIPT);
        given(geminiClient.extractListingIntent(anyString()))
                .willReturn(new GeminiClient.GeminiExtractionResult(SAMPLE_TRANSCRIPT, intent));

        VoiceListingResponse response = service.processVoiceRecording(webmFile("recording.webm"));

        assertThat(response.listing().getQuality()).isEqualTo(Quality.UNKNOWN);
    }

    // ─── invalid audio ───────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw AiServiceException(INVALID_AUDIO) for empty file")
    void shouldThrowForEmptyAudio() {
        MockMultipartFile emptyAudio = new MockMultipartFile(
                "audio", "recording.webm", "audio/webm", new byte[0]
        );

        assertThatThrownBy(() -> service.processVoiceRecording(emptyAudio))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("INVALID_AUDIO"));
    }

    @Test
    @DisplayName("should throw AiServiceException(INVALID_AUDIO) for null file")
    void shouldThrowForNullAudio() {
        assertThatThrownBy(() -> service.processVoiceRecording(null))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("INVALID_AUDIO"));
    }

    // ─── FFmpeg failure ──────────────────────────────────────────────────────

    @Test
    @DisplayName("should propagate AiServiceException(AUDIO_CONVERSION_FAILED) when FFmpeg fails")
    void shouldPropagateConversionFailure() {
        given(audioConverter.convertToWav(any()))
                .willThrow(new AiServiceException("AUDIO_CONVERSION_FAILED", "FFmpeg not found."));

        assertThatThrownBy(() -> service.processVoiceRecording(webmFile("recording.webm")))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("AUDIO_CONVERSION_FAILED"));

        then(whisperClient).shouldHaveNoInteractions();
        then(geminiClient).shouldHaveNoInteractions();
    }

    // ─── Whisper failures ────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw AiServiceException(AI_SERVICE_UNAVAILABLE) when Whisper is down")
    void shouldThrowWhenWhisperUnavailable() {
        given(audioConverter.convertToWav(any())).willReturn(FAKE_WAV);
        given(whisperClient.transcribe(any(byte[].class), anyString()))
                .willThrow(new AiServiceException("AI_SERVICE_UNAVAILABLE", "Whisper service unreachable."));

        assertThatThrownBy(() -> service.processVoiceRecording(webmFile("recording.webm")))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("AI_SERVICE_UNAVAILABLE"));

        then(geminiClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("should throw AiServiceException(TRANSCRIPTION_FAILED) when Whisper returns error")
    void shouldThrowWhenTranscriptionFails() {
        given(audioConverter.convertToWav(any())).willReturn(FAKE_WAV);
        given(whisperClient.transcribe(any(byte[].class), anyString()))
                .willThrow(new AiServiceException("TRANSCRIPTION_FAILED", "No speech detected."));

        assertThatThrownBy(() -> service.processVoiceRecording(webmFile("recording.webm")))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("TRANSCRIPTION_FAILED"));
    }

    // ─── Gemini failures ─────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw AiServiceException(AI_SERVICE_UNAVAILABLE) when Gemini returns 503")
    void shouldThrowAiServiceUnavailableOnGemini503() {
        given(audioConverter.convertToWav(any())).willReturn(FAKE_WAV);
        given(whisperClient.transcribe(any(byte[].class), anyString())).willReturn(SAMPLE_TRANSCRIPT);
        given(geminiClient.extractListingIntent(anyString()))
                .willThrow(new AiServiceException("AI_SERVICE_UNAVAILABLE",
                        "The AI extraction service is temporarily unavailable."));

        assertThatThrownBy(() -> service.processVoiceRecording(webmFile("recording.webm")))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> {
                    AiServiceException aiEx = (AiServiceException) ex;
                    assertThat(aiEx.getErrorCode()).isEqualTo("AI_SERVICE_UNAVAILABLE");
                    // Must NOT say "could not understand your voice" — that's for transcription
                    assertThat(aiEx.getMessage()).doesNotContain("could not understand your voice");
                });
    }

    @Test
    @DisplayName("should throw AiServiceException(AI_EXTRACTION_FAILED) on malformed Gemini JSON")
    void shouldThrowAiExtractionFailedOnBadJson() {
        given(audioConverter.convertToWav(any())).willReturn(FAKE_WAV);
        given(whisperClient.transcribe(any(byte[].class), anyString())).willReturn(SAMPLE_TRANSCRIPT);
        given(geminiClient.extractListingIntent(anyString()))
                .willThrow(new AiServiceException("AI_EXTRACTION_FAILED",
                        "The AI returned an unexpected response format."));

        assertThatThrownBy(() -> service.processVoiceRecording(webmFile("recording.webm")))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("AI_EXTRACTION_FAILED"));
    }

    // ─── validation failure ──────────────────────────────────────────────────

    @Test
    @DisplayName("should throw ListingValidationException when extracted data is invalid")
    void shouldThrowValidationExceptionForInvalidData() {
        ListingIntentResponse intent = buildValidIntent();
        intent.setQuantity(null); // missing quantity — will fail validator
        given(audioConverter.convertToWav(any())).willReturn(FAKE_WAV);
        given(whisperClient.transcribe(any(byte[].class), anyString())).willReturn(SAMPLE_TRANSCRIPT);
        given(geminiClient.extractListingIntent(anyString()))
                .willReturn(new GeminiClient.GeminiExtractionResult(SAMPLE_TRANSCRIPT, intent));

        assertThatThrownBy(() -> service.processVoiceRecording(webmFile("recording.webm")))
                .isInstanceOf(ListingValidationException.class);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static MockMultipartFile webmFile(String name) {
        return new MockMultipartFile("audio", name, "audio/webm;codecs=opus",
                "fake-audio-data".getBytes());
    }

    private static ListingIntentResponse buildValidIntent() {
        ListingIntentResponse r = new ListingIntentResponse();
        r.setIntent(Intent.CREATE_LISTING);
        r.setProduct(Product.RAMBUTAN);
        r.setQuantity(new BigDecimal("50"));
        r.setUnit(Unit.KG);
        r.setQuality(Quality.GOOD);
        r.setPricePerUnit(new BigDecimal("450"));
        return r;
    }
}
