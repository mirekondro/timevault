package com.example.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AiContextService {

    private static final DateTimeFormatter PROMPT_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "have", "about", "into", "after", "before",
            "they", "their", "there", "today", "denmark", "says", "will", "just", "news", "what", "when",
            "over", "under", "your", "more", "less", "very", "ikke", "med", "det", "der", "som", "til", "fra"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiContextService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String generateArchiveContext(LocalDateTime dateTime, String title, String content) {
        String prompt = """
                Today is %s. The following content was saved from the internet:

                Title: %s
                Content: %s

                Write exactly 3 sentences:
                1. What was happening in the world at this moment in time
                2. Why this content is culturally significant
                3. What a historian in 2075 would find interesting about it

                Be specific. Be human. Do not be generic.
                """.formatted(
                dateTime.format(PROMPT_DATE),
                safeText(title),
                safeText(limit(content, 500))
        );

        return requestModel(prompt).orElseGet(() -> fallbackArchiveContext(dateTime, title, content));
    }

    public String generateDailyVibe(LocalDate date, List<String> headlines, List<String> topics) {
        String prompt = """
                Today is %s. These are some Danish headlines:
                %s

                Trending topics: %s

                Write exactly 3 sentences:
                1. What Denmark felt like today
                2. What people were likely paying attention to
                3. What future historians would learn from this mood snapshot
                """.formatted(
                date.format(PROMPT_DATE),
                headlines.stream().limit(8).map(headline -> "- " + headline).collect(Collectors.joining(System.lineSeparator())),
                String.join(", ", topics)
        );

        return requestModel(prompt).orElseGet(() -> fallbackDailyVibe(date, headlines, topics));
    }

    public List<String> extractKeywords(String text, int limit) {
        Map<String, Long> counts = new LinkedHashMap<>();
        String[] parts = safeText(text).toLowerCase(Locale.ENGLISH).split("[^a-z0-9]+");
        for (String part : parts) {
            if (part.length() < 4 || STOP_WORDS.contains(part)) {
                continue;
            }
            counts.merge(part, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private java.util.Optional<String> requestModel(String prompt) {
        String provider = System.getenv().getOrDefault("TIMEVAULT_AI_PROVIDER", "").trim().toLowerCase(Locale.ENGLISH);
        String geminiKey = System.getenv("GEMINI_API_KEY");
        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");

        try {
            if ("gemini".equals(provider) || (provider.isBlank() && geminiKey != null && !geminiKey.isBlank())) {
                return java.util.Optional.of(callGemini(prompt, geminiKey));
            }
            if ("anthropic".equals(provider) || (provider.isBlank() && anthropicKey != null && !anthropicKey.isBlank())) {
                return java.util.Optional.of(callAnthropic(prompt, anthropicKey));
            }
        } catch (Exception ignored) {
        }

        return java.util.Optional.empty();
    }

    private String callGemini(String prompt, String apiKey) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing GEMINI_API_KEY");
        }

        String payload = objectMapper.writeValueAsString(Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.8,
                        "maxOutputTokens", 280
                )
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Gemini request failed with HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new IllegalStateException("Gemini returned an empty response");
        }
        return textNode.asText().trim();
    }

    private String callAnthropic(String prompt, String apiKey) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing ANTHROPIC_API_KEY");
        }

        String payload = objectMapper.writeValueAsString(Map.of(
                "model", "claude-3-5-haiku-latest",
                "max_tokens", 280,
                "temperature", 0.8,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Anthropic request failed with HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode textNode = root.path("content").path(0).path("text");
        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new IllegalStateException("Anthropic returned an empty response");
        }
        return textNode.asText().trim();
    }

    private String fallbackArchiveContext(LocalDateTime dateTime, String title, String content) {
        List<String> keywords = extractKeywords(title + " " + limit(content, 1000), 3);
        String subject = keywords.isEmpty() ? "a fast-moving online moment" : String.join(", ", keywords);
        List<String> sentences = new ArrayList<>();
        sentences.add("On %s, this capture sits inside a web culture shaped by constant updates, shared headlines, and people reacting in public as events unfolded.".formatted(dateTime.format(PROMPT_DATE)));
        sentences.add("Its cultural weight comes from how it freezes attention around %s, showing what felt worth saving in the middle of the scroll instead of years later in hindsight.".formatted(subject));
        sentences.add("A historian in 2075 would care about the tone, speed, and framing here because it shows not only the information people saw, but the texture of living online in real time.");
        return String.join(" ", sentences);
    }

    private String fallbackDailyVibe(LocalDate date, List<String> headlines, List<String> topics) {
        String joinedTopics = topics.isEmpty() ? "public life, media, and daily conversation" : String.join(", ", topics);
        String leadHeadline = headlines.isEmpty() ? "news moved quickly across a mix of civic and cultural stories" : headlines.get(0);
        return "On %s, Denmark felt tuned into %s, with a public mood shaped by headlines like \"%s\".".formatted(date.format(PROMPT_DATE), joinedTopics, leadHeadline)
                + " People were likely splitting their attention between practical concerns and the broader national conversation, reacting in real time as stories developed."
                + " For future historians, this kind of capsule shows how a single day carried both information and atmosphere, not just isolated facts.";
    }

    private String safeText(String input) {
        return input == null ? "" : input.replaceAll("\\s+", " ").trim();
    }

    private String limit(String input, int maxLength) {
        String value = safeText(input);
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}
