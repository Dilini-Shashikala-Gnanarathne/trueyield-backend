package com.trueyield.ai.controller;

import com.trueyield.ai.dto.ApiResponse;
import com.trueyield.ai.dto.VoiceListingResponse;
import com.trueyield.ai.service.VoiceListingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Thin controller — delegates all logic to VoiceListingService.
 *
 * Endpoint: POST /api/v1/ai/voice-to-listing
 * Accept:   multipart/form-data  (field: "audio")
 */
@RestController
@RequestMapping("/api/v1/ai")
public class VoiceListingController {

    private static final Logger log = LoggerFactory.getLogger(VoiceListingController.class);

    private final VoiceListingService voiceListingService;

    public VoiceListingController(VoiceListingService voiceListingService) {
        this.voiceListingService = voiceListingService;
    }

    /**
     * Accept a farmer's voice recording and extract structured listing information.
     *
     * @param audio the recorded audio file (WebM, MP4, OGG, WAV)
     * @return validated listing intent and transcript
     */
    @PostMapping(
            value = "/voice-to-listing",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<VoiceListingResponse>> processVoice(
            @RequestPart("audio") MultipartFile audio
    ) {
        log.info("Received voice listing request");
        VoiceListingResponse response = voiceListingService.processVoiceRecording(audio);
        log.info("Voice listing processed successfully");
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
