package com.example.test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class GeminiTest {

    public static void main(String[] args) {
        testGeminiAPI();
    }

    public static void testGeminiAPI() {
        String apiKey = "AIzaSyBoNPRhLSHJn6BFLMt6fZeNqTDWNn-jwrc";
        String model = "gemini-2.5-flash";

        try {
            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

            ObjectMapper objectMapper = new ObjectMapper();

            String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", model, apiKey);

            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = requestBody.putArray("contents");
            ObjectNode content = contents.addObject();
            content.put("role", "user");
            ArrayNode parts = content.putArray("parts");
            parts.addObject().put("text", "Say hello in one sentence.");

            // Add generation config for concise responses
            ObjectNode generationConfig = requestBody.putObject("generationConfig");
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 100);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(30))
                .build();

            System.out.println("Testing Gemini API...");
            System.out.println("Request URL: " + url);
            System.out.println("Request Body: " + objectMapper.writeValueAsString(requestBody));

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response Status: " + response.statusCode());
            System.out.println("Response Body: " + response.body());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
                System.out.println("SUCCESS! Gemini response: " + text.asText());
            } else {
                System.out.println("ERROR! Status: " + response.statusCode());
                System.out.println("Error response: " + response.body());
            }

        } catch (Exception e) {
            System.out.println("Exception occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
