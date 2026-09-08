package com.trueyield.ai.service;

import com.trueyield.ai.config.WhisperConfig;
import com.trueyield.ai.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Converts any browser-uploaded audio file to a Whisper-compatible format using FFmpeg.
 *
 * <p>Output format: 16 kHz, mono, signed 16-bit little-endian PCM WAV.
 *
 * <p>The FFmpeg executable path is fully configurable via {@code ffmpeg.path} in application.yml
 * (or the {@code FFMPEG_PATH} environment variable). It is never hardcoded.
 *
 * <p>All temporary files are cleaned up in a finally block.
 *
 * <p>Throws {@link AiServiceException} with code {@code AUDIO_CONVERSION_FAILED} on failure.
 */
@Component
public class AudioConverter {

    private static final Logger log = LoggerFactory.getLogger(AudioConverter.class);

    /** FFmpeg conversion timeout (seconds). */
    private static final int FFMPEG_TIMEOUT_SECONDS = 60;

    private final WhisperConfig config;

    public AudioConverter(WhisperConfig config) {
        this.config = config;
    }

    /**
     * Convert the uploaded audio {@link MultipartFile} to a WAV byte array.
     *
     * @param audioFile the multipart upload from the browser
     * @return 16 kHz mono PCM WAV bytes
     * @throws AiServiceException if FFmpeg is missing, conversion fails, or times out
     */
    public byte[] convertToWav(MultipartFile audioFile) {
        validateNotEmpty(audioFile);

        Path tmpDir = null;
        Path inputFile = null;
        Path wavFile = null;

        try {
            tmpDir = Files.createTempDirectory("trueyield-audio-");
            String id = UUID.randomUUID().toString();

            String inputExt = guessExtension(audioFile.getOriginalFilename());
            inputFile = tmpDir.resolve(id + "_input" + inputExt);
            wavFile   = tmpDir.resolve(id + "_audio.wav");

            // Write the uploaded bytes to disk so FFmpeg can read them
            try (InputStream in = audioFile.getInputStream()) {
                Files.copy(in, inputFile);
            }

            log.info("Audio conversion started — input: {}, size: {} bytes",
                    inputFile.getFileName(), Files.size(inputFile));

            runFfmpeg(inputFile, wavFile);

            byte[] wavBytes = Files.readAllBytes(wavFile);
            log.info("Audio conversion completed — WAV size: {} bytes", wavBytes.length);
            return wavBytes;

        } catch (AiServiceException ex) {
            throw ex; // already wrapped
        } catch (IOException ex) {
            log.error("I/O error during audio conversion", ex);
            throw new AiServiceException(
                    "AUDIO_CONVERSION_FAILED",
                    "Failed to process the audio file. Please try recording again.",
                    ex
            );
        } finally {
            deleteSilently(inputFile);
            deleteSilently(wavFile);
            deleteSilently(tmpDir);
        }
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private void validateNotEmpty(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new AiServiceException(
                    "INVALID_AUDIO",
                    "The audio file is empty or missing. Please record again."
            );
        }
    }

    private void runFfmpeg(Path input, Path output) {
        List<String> cmd = List.of(
                config.getFfmpegPath(),
                "-y",                          // overwrite output without asking
                "-i", input.toAbsolutePath().toString(),
                "-vn",                         // discard video stream if present
                "-ar", "16000",                // sample rate: 16 kHz
                "-ac", "1",                    // channels: mono
                "-f", "wav",                   // output container
                "-acodec", "pcm_s16le",        // codec: signed 16-bit PCM little-endian
                output.toAbsolutePath().toString()
        );

        log.debug("FFmpeg command: {}", String.join(" ", cmd));

        Process process;
        try {
            process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)  // merge stderr into stdout
                    .start();
        } catch (IOException ex) {
            String path = config.getFfmpegPath();
            log.error("FFmpeg not found at '{}': {}", path, ex.getMessage());
            throw new AiServiceException(
                    "AUDIO_CONVERSION_FAILED",
                    String.format(
                            "FFmpeg was not found at '%s'. " +
                            "Install FFmpeg and set 'ffmpeg.path' in application.yml " +
                            "or the FFMPEG_PATH environment variable.",
                            path
                    ),
                    ex
            );
        }

        // Drain stdout/stderr to avoid blocking (ProcessBuilder.redirectErrorStream merges both)
        byte[] ffmpegOutput;
        try {
            ffmpegOutput = process.getInputStream().readAllBytes();
        } catch (IOException ex) {
            ffmpegOutput = new byte[0];
        }

        // Wait with timeout
        boolean finished;
        try {
            finished = process.waitFor(FFMPEG_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new AiServiceException(
                    "AUDIO_CONVERSION_FAILED",
                    "Audio conversion was interrupted. Please try again."
            );
        }

        if (!finished) {
            process.destroyForcibly();
            throw new AiServiceException(
                    "AUDIO_CONVERSION_FAILED",
                    "Audio conversion timed out. The recording may be too long or corrupted."
            );
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String stderr = new String(ffmpegOutput, java.nio.charset.StandardCharsets.UTF_8);
            log.error("FFmpeg exited with code {} — output: {}", exitCode,
                    stderr.length() > 500 ? stderr.substring(0, 500) + "..." : stderr);
            throw new AiServiceException(
                    "AUDIO_CONVERSION_FAILED",
                    "The audio file could not be converted. It may be corrupted or in an unsupported format."
            );
        }

        log.debug("FFmpeg completed with exit code 0");
    }

    /**
     * Guess the file extension from the original filename.
     * Defaults to {@code .webm} (the browser's default recording format).
     */
    private String guessExtension(String filename) {
        if (filename == null) return ".webm";
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return ".webm";
        String ext = filename.substring(dot).toLowerCase();
        return switch (ext) {
            case ".webm", ".mp4", ".ogg", ".wav", ".mp3", ".m4a", ".flac" -> ext;
            default -> ".webm";
        };
    }

    private void deleteSilently(Path path) {
        if (path != null) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
        }
    }
}
