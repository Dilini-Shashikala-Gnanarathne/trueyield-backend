package com.trueyield.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configuration properties for the local Whisper STT service, FFmpeg, and voice upload limits.
 *
 * Bound from application.yml:
 * <pre>
 * whisper:
 *   url: http://localhost:9000
 *   model: base
 *   language: auto
 *   timeout-seconds: 60
 *
 * ffmpeg:
 *   path: ffmpeg
 *
 * voice:
 *   max-file-size-mb: 10
 * </pre>
 */
@Configuration
public class WhisperConfig {

    // ─── Whisper service ─────────────────────────────────────────────────────

    @org.springframework.beans.factory.annotation.Value("${whisper.url:http://localhost:9000}")
    private String whisperUrl;

    @org.springframework.beans.factory.annotation.Value("${whisper.model:base}")
    private String whisperModel;

    @org.springframework.beans.factory.annotation.Value("${whisper.language:auto}")
    private String whisperLanguage;

    @org.springframework.beans.factory.annotation.Value("${whisper.timeout-seconds:60}")
    private int whisperTimeoutSeconds;

    // ─── FFmpeg ──────────────────────────────────────────────────────────────

    @org.springframework.beans.factory.annotation.Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    // ─── Voice upload limits ─────────────────────────────────────────────────

    @org.springframework.beans.factory.annotation.Value("${voice.max-file-size-mb:10}")
    private int maxFileSizeMb;

    // ─── Accessors ───────────────────────────────────────────────────────────

    public String getWhisperUrl()      { return whisperUrl; }
    public String getWhisperModel()    { return whisperModel; }
    public String getWhisperLanguage() { return whisperLanguage; }
    public Duration getWhisperTimeout(){ return Duration.ofSeconds(whisperTimeoutSeconds); }
    public String getFfmpegPath()      { return ffmpegPath; }
    public long getMaxFileSizeBytes()  { return (long) maxFileSizeMb * 1024 * 1024; }

    @Bean("whisperRestClient")
    public RestClient whisperRestClient() {
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(whisperTimeoutSeconds));

        return RestClient.builder()
                .baseUrl(whisperUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
