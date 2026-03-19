package com.example.web.api;

import com.example.shared.service.ElevenLabsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API Controller for ElevenLabs Text-to-Speech integration
 */
@RestController
@RequestMapping("/api/speech")
@CrossOrigin(origins = "*")
public class SpeechApiController {

    private final ElevenLabsService elevenLabsService;

    @Autowired
    public SpeechApiController(ElevenLabsService elevenLabsService) {
        this.elevenLabsService = elevenLabsService;
    }

    /**
     * Convert text to speech
     */
    @PostMapping("/synthesize")
    public ResponseEntity<Map<String, Object>> synthesizeText(@RequestBody TextToSpeechRequest request) {
        try {
            if (request.getText() == null || request.getText().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Text is required"));
            }

            String audioBase64 = elevenLabsService.textToSpeech(request.getText());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("audio", "data:audio/mpeg;base64," + audioBase64);
            response.put("message", "Audio generated successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to generate audio: " + e.getMessage()));
        }
    }

    /**
     * Get available voices
     */
    @GetMapping("/voices")
    public ResponseEntity<Map<String, Object>> getVoices() {
        try {
            String voicesJson = elevenLabsService.getVoices();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("voices", voicesJson);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to get voices: " + e.getMessage()));
        }
    }

    /**
     * Check service status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("available", elevenLabsService.isServiceAvailable());
        response.put("service", "ElevenLabs TTS");
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    /**
     * Request DTO for text-to-speech
     */
    public static class TextToSpeechRequest {
        private String text;
        private String voiceId;

        public TextToSpeechRequest() {}

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getVoiceId() { return voiceId; }
        public void setVoiceId(String voiceId) { this.voiceId = voiceId; }
    }
}
