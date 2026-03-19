package com.example.shared.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * SHARED SERVICE - Gemini AI Integration
 *
 * Uses Gemini 2.0 Flash for:
 * - Image analysis and embedding
 * - Text context generation (3 sentences)
 * - URL content summarization
 */
@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    public GeminiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check if Gemini API is configured
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Generate a 3-sentence context for text content
     */
    public String generateTextContext(String content) {
        if (!isConfigured()) {
            return generateFallbackContext(content, "TEXT");
        }

        try {
            String prompt = """
                Analyze the following text and provide exactly 3 concise sentences that summarize:
                1. What this content is about
                2. The key information or main points
                3. Why it might be useful to save
                
                Text to analyze:
                %s
                
                Respond with only the 3 sentences, no numbering or bullets.
                """.formatted(truncate(content, 2000));

            return callGemini(prompt, null);
        } catch (Exception e) {
            return generateFallbackContext(content, "TEXT");
        }
    }

    /**
     * Generate a 3-sentence context for a URL
     */
    public String generateUrlContext(String url, String pageContent) {
        if (!isConfigured()) {
            return generateFallbackContext(url, "URL");
        }

        try {
            String prompt = """
                Analyze this web page and provide exactly 3 concise sentences that summarize:
                1. What this page is about
                2. The key information it contains
                3. Why it might be useful to save
                
                URL: %s
                Page content:
                %s
                
                Respond with only the 3 sentences, no numbering or bullets.
                """.formatted(url, truncate(pageContent, 3000));

            return callGemini(prompt, null);
        } catch (Exception e) {
            return generateFallbackContext(url, "URL");
        }
    }

    /**
     * Analyze an image and generate context using Gemini Vision
     */
    public String analyzeImage(byte[] imageData, String mimeType, String filename) {
        if (!isConfigured()) {
            return generateFallbackContext(filename, "IMAGE");
        }

        try {
            String prompt = """
                Analyze this image and provide exactly 3 concise sentences that describe:
                1. What the image shows or contains
                2. Any important details, text, or elements visible
                3. The likely purpose or context of this image
                
                Respond with only the 3 sentences, no numbering or bullets.
                """;

            return callGeminiWithImage(prompt, imageData, mimeType);
        } catch (Exception e) {
            return generateFallbackContext(filename, "IMAGE");
        }
    }

    /**
     * Analyze an image from a file path
     */
    public String analyzeImage(Path imagePath) {
        try {
            byte[] imageData = Files.readAllBytes(imagePath);
            String mimeType = Files.probeContentType(imagePath);
            if (mimeType == null) {
                mimeType = "image/jpeg";
            }
            return analyzeImage(imageData, mimeType, imagePath.getFileName().toString());
        } catch (IOException e) {
            return generateFallbackContext(imagePath.getFileName().toString(), "IMAGE");
        }
    }

    /**
     * Generate embedding for text (for future semantic search)
     */
    public float[] generateEmbedding(String text) {
        if (!isConfigured()) {
            return new float[0];
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=" + apiKey;

            ObjectNode requestBody = objectMapper.createObjectNode();
            ObjectNode content = requestBody.putObject("content");
            ArrayNode parts = content.putArray("parts");
            parts.addObject().put("text", truncate(text, 2000));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode values = root.path("embedding").path("values");
                if (values.isArray()) {
                    float[] embedding = new float[values.size()];
                    for (int i = 0; i < values.size(); i++) {
                        embedding[i] = (float) values.get(i).asDouble();
                    }
                    return embedding;
                }
            }
        } catch (Exception e) {
            // Log error but don't throw
        }
        return new float[0];
    }

    // ============================================
    // PRIVATE HELPER METHODS
    // ============================================

    private String callGemini(String prompt, String systemInstruction) throws Exception {
        String url = String.format(GEMINI_API_URL, model, apiKey);

        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode contents = requestBody.putArray("contents");
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);

        // Add generation config for concise responses
        ObjectNode generationConfig = requestBody.putObject("generationConfig");
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 200);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
            return text.asText();
        } else {
            throw new RuntimeException("Gemini API error: " + response.statusCode());
        }
    }

    private String callGeminiWithImage(String prompt, byte[] imageData, String mimeType) throws Exception {
        String url = String.format(GEMINI_API_URL, model, apiKey);

        String base64Image = Base64.getEncoder().encodeToString(imageData);

        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode contents = requestBody.putArray("contents");
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");

        // Add text prompt
        parts.addObject().put("text", prompt);

        // Add image
        ObjectNode imagePart = parts.addObject();
        ObjectNode inlineData = imagePart.putObject("inlineData");
        inlineData.put("mimeType", mimeType);
        inlineData.put("data", base64Image);

        // Add generation config
        ObjectNode generationConfig = requestBody.putObject("generationConfig");
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 200);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
            return text.asText();
        } else {
            throw new RuntimeException("Gemini API error: " + response.statusCode() + " - " + response.body());
        }
    }

    private String generateFallbackContext(String content, String type) {
        String preview = truncate(content, 100);
        return switch (type) {
            case "URL" -> "Saved web page from " + extractDomain(content) + ". Content archived for offline access. Auto-tagged by date and platform.";
            case "IMAGE" -> "Image saved to vault. Visual content stored locally. Ready for future reference and search.";
            case "TEXT" -> "Text note: " + preview + " Saved for quick access. Tagged and timestamped automatically.";
            default -> "Content saved to TimeVault. Available for search and browsing. Auto-organized by type and date.";
        };
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null ? uri.getHost() : url;
        } catch (Exception e) {
            return url.length() > 30 ? url.substring(0, 30) + "..." : url;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}

