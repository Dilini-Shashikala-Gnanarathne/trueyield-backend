package com.trueyield.ai.service;

import com.trueyield.ai.dto.ListingIntentResponse;
import com.trueyield.ai.dto.VoiceListingResponse;
import com.trueyield.ai.enums.Quality;
import com.trueyield.ai.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates the voice-to-listing pipeline:
 *
 * <pre>
 *   MultipartFile (WebM/Opus)
 *     → AudioConverter  (FFmpeg → 16 kHz mono WAV)
 *     → WhisperClient   (local STT → plain-text transcript)
 *     → GeminiClient    (text-only prompt → structured JSON)
 *     → ListingIntentValidator
 *     → VoiceListingResponse
 * </pre>
 *
 * <p>Audio bytes are NEVER forwarded to Gemini. Gemini receives transcript text only.
 *
 * <p>This service deliberately has NO repository dependency.
 * It extracts and validates data only. The Marketplace Service creates listings.
 */
@Service
public class VoiceListingService {

    private static final Logger log = LoggerFactory.getLogger(VoiceListingService.class);

    /** Maximum audio file size accepted at the service level (10 MB). */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final AudioConverter          audioConverter;
    private final WhisperClient           whisperClient;
    private final GeminiClient            geminiClient;
    private final ListingIntentValidator  validator;

    public VoiceListingService(
            AudioConverter         audioConverter,
            WhisperClient          whisperClient,
            GeminiClient           geminiClient,
            ListingIntentValidator validator
    ) {
        this.audioConverter = audioConverter;
        this.whisperClient  = whisperClient;
        this.geminiClient   = geminiClient;
        this.validator      = validator;
    }

    /**
     * Process a voice recording and extract structured listing information.
     *
     * @param audioFile the uploaded audio recording from the farmer
     * @return structured, validated listing intent + original transcript
     * @throws AiServiceException        if transcription or Gemini extraction fails
     * @throws IllegalArgumentException  if the audio file is missing, empty, or too large
     */
    public VoiceListingResponse processVoiceRecording(MultipartFile audioFile) {

        // ── 1. Validate upload ───────────────────────────────────────────────
        validateAudioFile(audioFile);

        log.info("Voice listing request received — filename: {}, size: {} bytes, contentType: {}",
                audioFile.getOriginalFilename(),
                audioFile.getSize(),
                audioFile.getContentType());

        // ── 2. Convert WebM/Opus → 16 kHz mono WAV via FFmpeg ───────────────
        log.info("Audio conversion started");
        byte[] wavBytes = audioConverter.convertToWav(audioFile);
        log.info("Audio conversion completed — WAV size: {} bytes", wavBytes.length);

        // ── 3. Transcribe WAV with local Whisper ─────────────────────────────
        log.info("Whisper transcription started");
        String transcript = whisperClient.transcribe(wavBytes, audioFile.getOriginalFilename());
        log.info("Whisper transcription completed — transcript length: {} chars", transcript.length());
        log.debug("Transcript content: '{}'", transcript); // full text only at DEBUG level

        // ── 4. Extract structured intent via Gemini (text only) ──────────────
        log.info("Gemini text extraction started");
        GeminiClient.GeminiExtractionResult result = geminiClient.extractListingIntent(transcript);
        log.info("Gemini extraction completed — intent: {}", result.intent());

        // ── 5. Default quality to UNKNOWN if Gemini could not determine it ───
        ListingIntentResponse intent = result.intent();
        if (intent.getQuality() == null) {
            intent.setQuality(Quality.UNKNOWN);
        }

        // ── 6. Validate extracted data ───────────────────────────────────────
        log.info("Listing validation started");
        validator.validate(intent);
        log.info("Listing validation completed — all fields valid");

        log.info("Voice listing processing completed successfully");
        return new VoiceListingResponse(transcript, intent);
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private void validateAudioFile(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new AiServiceException(
                    "INVALID_AUDIO",
                    "Audio file is empty or missing. Please record again."
            );
        }
        if (audioFile.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new AiServiceException(
                    "INVALID_AUDIO",
                    "Audio file is too large. Maximum size is " + (MAX_FILE_SIZE_BYTES / (1024 * 1024)) + " MB."
            );
        }
    }
}
