package com.example.shared.service;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

@Service
public class ElevenLabsService {

    private static final String API_KEY = "sk_19d262f237c4d3f5b4d74555eb97e4ae2270e7a4a564e6d0";
    private static final String BASE_URL = "https://api.elevenlabs.io/v1";
    private static final String DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM"; // Rachel voice

    private final HttpClient httpClient;

    public ElevenLabsService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Convert text to speech using ElevenLabs API
     * @param text The text to convert to speech
     * @return Base64 encoded audio data
     */
    public String textToSpeech(String text) throws IOException, InterruptedException {
        return textToSpeech(text, DEFAULT_VOICE_ID);
    }

    /**
     * Convert text to speech using ElevenLabs API with specific voice
     * @param text The text to convert to speech
     * @param voiceId The voice ID to use
     * @return Base64 encoded audio data
     */
    public String textToSpeech(String text, String voiceId) throws IOException, InterruptedException {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }

        // Limit text length to avoid API limits
        if (text.length() > 2500) {
            text = text.substring(0, 2500) + "...";
        }

        String requestBody = String.format("""
            {
                "text": "%s",
                "model_id": "eleven_turbo_v2",
                "voice_settings": {
                    "stability": 0.5,
                    "similarity_boost": 0.75,
                    "style": 0.0,
                    "use_speaker_boost": true
                }
            }
            """, escapeJson(text));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/text-to-speech/" + voiceId))
                .header("Accept", "audio/mpeg")
                .header("Content-Type", "application/json")
                .header("xi-api-key", API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("ElevenLabs API error: " + response.statusCode() + " - " + new String(response.body()));
        }

        // Convert audio bytes to base64 for frontend
        return Base64.getEncoder().encodeToString(response.body());
    }

    /**
     * Get available voices from ElevenLabs
     * @return JSON string with available voices
     */
    public String getVoices() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/voices"))
                .header("Accept", "application/json")
                .header("xi-api-key", API_KEY)
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("ElevenLabs API error: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    /**
     * Check if ElevenLabs service is available
     */
    public boolean isServiceAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/user"))
                    .header("xi-api-key", API_KEY)
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
