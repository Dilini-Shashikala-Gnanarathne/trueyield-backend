package com.trueyield.ai.service;

import com.trueyield.ai.config.WhisperConfig;
import com.trueyield.ai.exception.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for AudioConverter.
 *
 * These tests do NOT invoke real FFmpeg — they verify error handling and validation logic.
 * The FFmpeg path is set to a non-existent binary to simulate "FFmpeg not installed".
 */
@DisplayName("AudioConverter")
class AudioConverterTest {

    private WhisperConfig config;
    private AudioConverter converter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        config = mock(WhisperConfig.class);
        given(config.getFfmpegPath()).willReturn("ffmpeg");
        converter = new AudioConverter(config);
    }

    // ─── validation ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw INVALID_AUDIO for empty file")
    void shouldThrowForEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile(
                "audio", "recording.webm", "audio/webm", new byte[0]
        );

        assertThatThrownBy(() -> converter.convertToWav(empty))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("INVALID_AUDIO"));
    }

    @Test
    @DisplayName("should throw INVALID_AUDIO for null file")
    void shouldThrowForNullFile() {
        assertThatThrownBy(() -> converter.convertToWav(null))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("INVALID_AUDIO"));
    }

    // ─── FFmpeg not found ─────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw AUDIO_CONVERSION_FAILED when FFmpeg binary does not exist")
    void shouldThrowWhenFfmpegNotFound() {
        given(config.getFfmpegPath()).willReturn("non-existent-ffmpeg-binary-xyz");
        converter = new AudioConverter(config);

        MockMultipartFile audio = new MockMultipartFile(
                "audio", "recording.webm", "audio/webm", "fake-audio".getBytes()
        );

        assertThatThrownBy(() -> converter.convertToWav(audio))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> {
                    AiServiceException aiEx = (AiServiceException) ex;
                    assertThat(aiEx.getErrorCode()).isEqualTo("AUDIO_CONVERSION_FAILED");
                    assertThat(aiEx.getMessage()).contains("non-existent-ffmpeg-binary-xyz");
                });
    }

    // ─── invalid audio content ────────────────────────────────────────────────

    @Test
    @DisplayName("should throw AUDIO_CONVERSION_FAILED when FFmpeg reports invalid audio content")
    void shouldThrowWhenFfmpegFailsOnInvalidAudio() {
        // FFmpeg will be found but will fail because "hello" is not valid audio
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "recording.webm", "audio/webm", "not-real-audio-data".getBytes()
        );

        // This test only runs if ffmpeg is actually on PATH; skip otherwise
        org.junit.jupiter.api.Assumptions.assumeTrue(ffmpegAvailable(),
                "Skipping: FFmpeg not found on PATH");

        assertThatThrownBy(() -> converter.convertToWav(audio))
                .isInstanceOf(AiServiceException.class)
                .satisfies(ex -> assertThat(((AiServiceException) ex).getErrorCode())
                        .isEqualTo("AUDIO_CONVERSION_FAILED"));
    }

    // ─── file extension guessing ──────────────────────────────────────────────

    @Test
    @DisplayName("should produce WAV bytes when FFmpeg is available and audio is valid")
    void shouldProduceWavBytesWhenFfmpegAvailable() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(ffmpegAvailable(),
                "Skipping: FFmpeg not found on PATH");

        // Minimal valid WAV file (44 byte header, 8-bit mono, 8000 Hz, "silent")
        byte[] minimalWav = buildMinimalWav();
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "recording.wav", "audio/wav", minimalWav
        );

        byte[] result = converter.convertToWav(audio);

        assertThat(result).isNotEmpty();
        // WAV files start with "RIFF"
        assertThat(new String(result, 0, 4)).isEqualTo("RIFF");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private boolean ffmpegAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Build a minimal valid silent WAV file (44-byte header + 1 second of silence at 8000 Hz, 8-bit mono).
     */
    private byte[] buildMinimalWav() {
        int sampleRate   = 8000;
        int numChannels  = 1;
        int bitsPerSample= 8;
        int numSamples   = sampleRate;  // 1 second
        int dataSize     = numSamples * numChannels * (bitsPerSample / 8);
        int chunkSize    = 36 + dataSize;

        byte[] wav = new byte[44 + dataSize];
        // RIFF header
        wav[0]='R'; wav[1]='I'; wav[2]='F'; wav[3]='F';
        intToLe(chunkSize, wav, 4);
        wav[8]='W'; wav[9]='A'; wav[10]='V'; wav[11]='E';
        // fmt sub-chunk
        wav[12]='f'; wav[13]='m'; wav[14]='t'; wav[15]=' ';
        intToLe(16, wav, 16); // sub-chunk size
        wav[20]=1;  wav[21]=0;  // PCM
        wav[22]=(byte)numChannels; wav[23]=0;
        intToLe(sampleRate, wav, 24);
        intToLe(sampleRate * numChannels * bitsPerSample / 8, wav, 28); // byte rate
        wav[32]=(byte)(numChannels * bitsPerSample / 8); wav[33]=0; // block align
        wav[34]=(byte)bitsPerSample; wav[35]=0;
        // data sub-chunk
        wav[36]='d'; wav[37]='a'; wav[38]='t'; wav[39]='a';
        intToLe(dataSize, wav, 40);
        // silence (128 = mid-point for unsigned 8-bit PCM)
        for (int i = 44; i < wav.length; i++) wav[i] = (byte)128;
        return wav;
    }

    private void intToLe(int value, byte[] buf, int offset) {
        buf[offset]   = (byte)(value);
        buf[offset+1] = (byte)(value >> 8);
        buf[offset+2] = (byte)(value >> 16);
        buf[offset+3] = (byte)(value >> 24);
    }
}
