package com.example.shared.service;

import com.example.shared.model.VaultItem;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * SHARED SERVICE - Gemini AI Integration
 *
 * Uses Gemini Flash for:
 * - Image analysis and embedding
 * - Text context generation (3 sentences)
 * - URL content summarization
 */
@Service
public class GeminiService {

    private static final List<String> LOW_VALUE_PAGE_PHRASES = List.of(
            "skip to content",
            "privacy policy",
            "terms of service",
            "cookie policy",
            "accept cookies",
            "all rights reserved",
            "sign up",
            "log in",
            "share this",
            "subscribe",
            "javascript",
            "newsletter");
    private static final List<String> GENERIC_URL_SUMMARY_PHRASES = List.of(
            "contains saved content",
            "archived locally",
            "offline access",
            "future reference",
            "searchable by keywords",
            "text, images, and interactive elements",
            "can be searched by url or content",
            "preserved for future reference");

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    public GeminiService() {
        this("", "gemini-2.5-flash");
    }

    public GeminiService(String apiKey, String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gemini-2.5-flash" : model.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check if Gemini API is configured
     */
    public boolean isConfigured() {
        boolean configured = apiKey != null && !apiKey.isEmpty();
        System.out.println("Gemini API configured: " + configured + " (API key: " + (apiKey != null ? apiKey.substring(0, Math.min(10, apiKey.length())) + "..." : "null") + ")");
        return configured;
    }

    /**
     * Test API connectivity with a simple text request
     */
    public String testConnection() {
        if (!isConfigured()) {
            return "API not configured";
        }

        try {
            return callGemini("Say hello in one sentence.", null);
        } catch (Exception e) {
            return "API test failed: " + e.getMessage();
        }
    }

    /**
     * Generate a proper title and context for URL content
     */
    public VaultItem generateUrlSummary(String url, String pageContent) {
        return generateUrlSummaryResult(url, pageContent).item();
    }

    public UrlSummaryResult generateUrlSummaryResult(String url, String pageContent) {
        if (!isConfigured()) {
            return new UrlSummaryResult(createFallbackUrlSummary(url, pageContent), false);
        }

        try {
            String prompt = """
                Analyze this webpage content and create a concrete, memorable summary that helps someone instantly remember what they saved. Provide your response in this EXACT format:
                
                TITLE: [Generate a clear, descriptive title - NO URLs, just describe what it is]
                DESCRIPTION: [Provide exactly 3 sentences about the ACTUAL CONTENT and WHY IT MATTERS]
                
                Requirements:
                - Be specific and concrete. Mention exact subjects, names, places, species, products, features, counts, dates, or sections when they appear.
                - Sentence 1: identify what the page is about, including the site/source and author/date if they are visible.
                - Sentence 2: summarize the most important facts, examples, lists, or sections with real details from the page.
                - Sentence 3: explain why this page would be worth saving or what makes it useful later.
                - Never use vague filler like "contains text, images, and interactive elements" unless that is genuinely the important point.
                - If the page is a list article, mention the size of the list and name representative items from it.
                
                Example of the level of detail:
                - "Article on reptilehere.com by Ali Ekram Fahim about 7 turtle species in the Philippines, covering five marine turtles and two freshwater turtles. It names species such as the green sea turtle, hawksbill, olive ridley, loggerhead, leatherback, Philippine forest turtle, and Southeast Asian box turtle, while also outlining their habitats, conservation status, and major threats. The page is useful as a wildlife reference because it combines species identification with conservation context and habitat information in one place."
                - "Documentation page on docs.spring.io for Spring Boot property binding, showing the supported configuration styles, example annotations, and validation options. It explains how `@ConfigurationProperties` maps external settings into typed Java objects, including constructor binding and nested properties. This page is worth saving as a practical reference when wiring application config or debugging binding behavior."
                
                URL: %s
                Content:
                %s
                """.formatted(url, truncate(pageContent, 3000));

            String response = callGemini(prompt, null);
            VaultItem parsedItem = parseUrlSummaryResponse(response, url, pageContent);
            if (!looksUsefulUrlSummary(parsedItem)) {
                return new UrlSummaryResult(createFallbackUrlSummary(url, pageContent), false);
            }
            return new UrlSummaryResult(parsedItem, true);
        } catch (Exception e) {
            return new UrlSummaryResult(createFallbackUrlSummary(url, pageContent), false);
        }
    }

    /**
     * Generate enhanced URL summary with detailed page analysis from Chrome extension
     */
    public VaultItem generateEnhancedUrlSummary(String url, String pageContent, String pageAnalysisJson) {
        if (!isConfigured()) {
            return createFallbackUrlSummary(url, pageContent);
        }

        try {
            String analysisInfo = "";
            if (pageAnalysisJson != null && !pageAnalysisJson.trim().isEmpty()) {
                analysisInfo = "\n\nPage Analysis: " + pageAnalysisJson;
            }

            String prompt = """
                Analyze this webpage content and create a rich, detailed summary using the provided page analysis. Provide your response in this EXACT format:
                
                TITLE: [Generate a clear, descriptive title - NO URLs, just describe what it is]
                DESCRIPTION: [Provide exactly 3 sentences that describe the ACTUAL CONTENT, what you see, what it contains, and what information it provides]
                
                Use the page analysis to provide specific details about:
                - What images are present (types, descriptions, count)
                - Text structure (word count, headings, topics)
                - Interactive elements (videos, forms, code, tables)
                - Specific content details and information
                
                Be very specific about what the page contains and what information it provides.
                
                URL: %s
                Content: %s%s
                """.formatted(url, truncate(pageContent, 2500), analysisInfo);

            String response = callGemini(prompt, null);
            VaultItem parsedItem = parseUrlSummaryResponse(response, url, pageContent);
            return parsedItem == null ? createFallbackUrlSummary(url, pageContent) : parsedItem;
        } catch (Exception e) {
            return createFallbackUrlSummary(url, pageContent);
        }
    }

    private VaultItem parseUrlSummaryResponse(String response, String url, String pageContent) {
        try {
            String normalizedResponse = sanitize(response).replace("```", "");
            String[] lines = normalizedResponse.split("\n");
            String title = "";
            String description = normalizedResponse;

            for (String line : lines) {
                if (line.startsWith("TITLE:")) {
                    title = line.substring(6).trim();
                } else if (line.startsWith("DESCRIPTION:")) {
                    description = line.substring(12).trim();
                    // Get the rest of the description if it spans multiple lines
                    int index = normalizedResponse.indexOf("DESCRIPTION:") + 12;
                    description = normalizedResponse.substring(index).trim();
                    break;
                }
            }

            title = sanitize(title);
            description = sanitize(description);
            if (title.isBlank()) {
                title = resolveFallbackTitle(url, pageContent);
            }
            if (description.isBlank()) {
                return null;
            }

            VaultItem item = new VaultItem();
            item.setTitle(title);
            item.setAiContext(description);
            item.setSourceUrl(url);
            item.setContent(truncate(pageContent, 5000)); // Store more content for search
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    private VaultItem createFallbackUrlSummary(String url, String pageContent) {
        VaultItem item = new VaultItem();

        String title = resolveFallbackTitle(url, pageContent);
        String description = buildFallbackUrlDescription(title, url, pageContent);

        item.setTitle(title);
        item.setAiContext(description);
        item.setSourceUrl(url);
        item.setContent(truncate(pageContent, 5000));
        return item;
    }

    private String resolveFallbackTitle(String url, String pageContent) {
        List<String> sentences = extractCandidateSentences(pageContent, 2);
        if (!sentences.isEmpty()) {
            String firstSentence = trimSentenceEnding(sentences.getFirst());
            if (looksLikeTitle(firstSentence)) {
                return firstSentence;
            }
        }
        return generateTitleFromUrl(url);
    }

    private String buildFallbackUrlDescription(String title, String url, String pageContent) {
        List<String> sentences = extractCandidateSentences(pageContent, 5);
        List<String> selected = new ArrayList<>();
        for (String sentence : sentences) {
            String trimmed = trimSentenceEnding(sentence);
            if (!trimmed.equalsIgnoreCase(title)) {
                selected.add(sentence);
            }
            if (selected.size() == 3) {
                break;
            }
        }

        if (selected.isEmpty()) {
            String domain = extractDomain(url);
            return String.format(
                    "%s from %s was saved for later reference. The page content could be read, but an AI summary was unavailable. You can still search this saved page by its title, URL, and extracted text.",
                    title,
                    domain);
        }

        while (selected.size() < 3) {
            if (selected.size() == 1) {
                selected.add(String.format("The page appears to be a useful reference saved from %s for later review.", extractDomain(url)));
            } else {
                selected.add("The extracted page text is stored so it can still be searched and reviewed later.");
            }
        }

        return String.join(" ", selected);
    }

    private String generateTitleFromUrl(String url) {
        try {
            String domain = extractDomain(url);
            String path = URI.create(url).getPath();

            // Clean up the domain name
            domain = domain.replace("www.", "").replace(".com", "").replace(".org", "").replace(".net", "");

            // Try to extract meaningful parts from path
            if (path != null && path.length() > 1) {
                String[] parts = path.split("/");
                for (String part : parts) {
                    if (!part.isEmpty() && !part.equals("index") && !part.equals("home")) {
                        String cleaned = part.replace("-", " ").replace("_", " ");
                        return capitalizeWords(domain + " - " + cleaned);
                    }
                }
            }

            return capitalizeWords(domain + " Page");
        } catch (Exception e) {
            return "Saved Webpage";
        }
    }

    private String capitalizeWords(String str) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : str.toCharArray()) {
            if (Character.isWhitespace(c) || c == '-') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    private List<String> extractCandidateSentences(String text, int limit) {
        List<String> sentences = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return sentences;
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        String[] parts = normalized.split("(?<=[.!?])\\s+");
        for (String part : parts) {
            String cleaned = part == null ? "" : part.trim();
            if (cleaned.isBlank()) {
                continue;
            }
            if (isLowValueSentence(cleaned)) {
                continue;
            }
            if (cleaned.length() < 35 && !looksLikeTitle(cleaned)) {
                continue;
            }
            if (cleaned.length() > 320) {
                cleaned = cleaned.substring(0, 320).trim() + "...";
            }
            if (!sentences.isEmpty() && sentences.getLast().equalsIgnoreCase(cleaned)) {
                continue;
            }
            sentences.add(ensureSentenceEnding(cleaned));
            if (sentences.size() >= limit) {
                break;
            }
        }
        return sentences;
    }

    private boolean isLowValueSentence(String value) {
        String normalized = sanitize(value).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return true;
        }
        for (String phrase : LOW_VALUE_PAGE_PHRASES) {
            if (normalized.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksUsefulUrlSummary(VaultItem item) {
        if (item == null) {
            return false;
        }

        String title = sanitize(item.getTitle());
        String description = sanitize(item.getAiContext());
        if (title.isBlank() || description.isBlank()) {
            return false;
        }
        if ("saved webpage".equalsIgnoreCase(title) || "saved link".equalsIgnoreCase(title)) {
            return false;
        }

        String normalizedDescription = description.toLowerCase(Locale.ROOT);
        for (String phrase : GENERIC_URL_SUMMARY_PHRASES) {
            if (normalizedDescription.contains(phrase)) {
                return false;
            }
        }

        String[] sentences = normalizedDescription.split("(?<=[.!?])\\s+");
        return sentences.length >= 2 && description.split("\\s+").length >= 18;
    }

    private boolean looksLikeTitle(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String cleaned = trimSentenceEnding(value);
        if (cleaned.length() < 8 || cleaned.length() > 120) {
            return false;
        }
        long wordCount = cleaned.split("\\s+").length;
        return wordCount >= 2 && wordCount <= 16;
    }

    private String ensureSentenceEnding(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        char lastChar = value.charAt(value.length() - 1);
        if (lastChar == '.' || lastChar == '!' || lastChar == '?') {
            return value;
        }
        return value + ".";
    }

    private String trimSentenceEnding(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[.!?]+$", "").trim();
    }

    /**
     * @deprecated Use generateUrlSummary instead
     */
    @Deprecated
    public String generateTextContext(String content) {
        if (!isConfigured()) {
            return generateFallbackContext(content, "TEXT");
        }

        try {
            String prompt = """
                Read this text content carefully and create a summary that captures the key information and main points. Provide exactly 3 sentences that tell someone WHAT THE TEXT SAYS, not what type of text it is.
                
                Focus on the actual CONTENT and INFORMATION:
                
                For NOTES: Summarize the main points, ideas, and key information
                For IDEAS: Explain the core concepts, proposals, or insights
                For INSTRUCTIONS: Describe what needs to be done and key steps
                For QUOTES: Explain the main message and context
                For DRAFTS: Summarize the main arguments or content being developed
                For MEETING NOTES: Cover key decisions, action items, and important discussion points
                
                Write as if you're explaining to someone what you just read. Be specific and informative.
                
                Examples:
                - Instead of: "These are meeting notes about project planning"
                - Write: "The team decided to launch the new feature in Q2, assigned Sarah to lead UI design, and set weekly check-ins every Thursday to track progress"
                
                - Instead of: "This is an idea about improving workflow"
                - Write: "Implement automated testing in the CI pipeline to catch bugs earlier, reduce manual QA time by 40%, and deploy releases twice weekly instead of monthly"
                
                Text to analyze:
                %s
                
                Respond with only the 3 detailed sentences, no numbering or bullets.
                """.formatted(truncate(content, 3000));

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
                Analyze this webpage content and create a detailed, actionable summary that will help the user remember exactly what they saved and why it's valuable. Provide exactly 3 comprehensive sentences:
                
                1. MAIN CONTENT & PURPOSE: What is this webpage about? Describe the specific topic, type of content (tutorial, article, documentation, product page, etc.), and the main subject matter. Include the website/source if identifiable.
                
                2. KEY INFORMATION & VALUE: What are the most important facts, insights, instructions, data, or takeaways? What specific problem does this solve or information does it provide? Include any important names, dates, numbers, or technical details.
                
                3. PRACTICAL USE & CONTEXT: Why would someone save this? What can they do with this information? Is it reference material, a how-to guide, important news, a tool, or something to buy/try later?
                
                Focus on creating a summary that answers "What is this?" and "Why did I save this?" with specific, memorable details that make it easy to find and recall later.
                
                URL: %s
                Content:
                %s
                
                Respond with only the 3 detailed sentences, no numbering or bullets.
                """.formatted(url, truncate(pageContent, 3000));

            return callGemini(prompt, null);
        } catch (Exception e) {
            return generateFallbackContext(url, "URL");
        }
    }

    /**
     * Create a combined AI summary for a gallery item using per-image analyses.
     */
    public String generateImageGallerySummary(String title, String notes, List<String> imageAnalyses) {
        List<String> normalizedAnalyses = imageAnalyses == null ? List.of() : imageAnalyses.stream()
                .map(this::sanitize)
                .filter(value -> !value.isBlank())
                .toList();
        if (normalizedAnalyses.isEmpty()) {
            return buildFallbackImageGallerySummary(title, notes, List.of());
        }

        if (!isConfigured()) {
            return buildFallbackImageGallerySummary(title, notes, normalizedAnalyses);
        }

        try {
            String prompt = """
                You are summarizing a saved gallery of images. Write exactly 3 sentences that help the user remember the gallery as one saved item.

                Requirements:
                - Sentence 1: explain the overall subject of the gallery and mention how many images it contains.
                - Sentence 2: combine the most important recurring details, text, scenes, objects, or topics from the image analyses.
                - Sentence 3: explain why this gallery is useful to keep or what kind of reference it provides later.
                - Be concrete and specific. Mention names, numbers, locations, products, documents, diagrams, or visible text when available.
                - Do not write generic filler like "these images were saved for future reference" unless that is the actual point.

                Gallery title: %s
                Shared notes: %s
                Number of images: %d
                Image analyses:
                %s

                Respond with only the 3 detailed sentences, no bullets or numbering.
                """.formatted(
                    sanitize(title),
                    truncate(sanitize(notes), 800),
                    normalizedAnalyses.size(),
                    formatGalleryAnalyses(normalizedAnalyses));

            return sanitize(callGemini(prompt, null));
        } catch (Exception exception) {
            return buildFallbackImageGallerySummary(title, notes, normalizedAnalyses);
        }
    }

    /**
     * Analyze an image and generate context using Gemini Vision
     */
    public String analyzeImage(byte[] imageData, String mimeType, String filename) {
        System.out.println("Analyzing image: " + filename + " (" + mimeType + "), size: " + imageData.length + " bytes");

        if (!isConfigured()) {
            System.out.println("Gemini API not configured, using fallback");
            return generateFallbackContext(filename, "IMAGE");
        }

        try {
            String prompt = """
                Analyze this image thoroughly and create a detailed, memorable description that will help the user recall exactly what they saved and why it might be important. Provide exactly 3 comprehensive sentences:
                
                1. CONTENT DESCRIPTION: What is actually shown in the image - describe specific objects, people, text, scenes, or activities in detail. Include colors, brands, locations, or any readable text.
                
                2. CONTEXT & PURPOSE: Explain the likely context, setting, or purpose. Is this a screenshot of something important? A photo of a document? A reference image? What story does it tell?
                
                3. KEY DETAILS & VALUE: Highlight specific details that make this image useful or memorable - important information, data, names, dates, instructions, or anything the user might want to find later.
                
                Make this description rich with searchable keywords and specific details that would help someone remember and find this image weeks or months later. Focus on practical value and memorability.
                
                Respond with only the 3 detailed sentences, no numbering or bullets.
                """;

            System.out.println("Calling Gemini API for detailed image analysis...");
            String result = callGeminiWithImage(prompt, imageData, mimeType);
            System.out.println("Gemini API response: " + result);
            return result;
        } catch (Exception e) {
            System.out.println("Gemini API error: " + e.getMessage());
            e.printStackTrace();
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

        System.out.println("Gemini API response status: " + response.statusCode());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
            String result = text.asText();
            System.out.println("Gemini API successful response: " + result.substring(0, Math.min(100, result.length())) + "...");
            return result;
        } else {
            System.out.println("Gemini API error response: " + response.body());
            throw new RuntimeException("Gemini API error: " + response.statusCode() + " - " + response.body());
        }
    }

    private String generateFallbackContext(String content, String type) {
        String preview = truncate(content, 200);
        return switch (type) {
            case "URL" -> String.format("Webpage saved from %s. Content archived locally for offline access including text, images and layout. This web resource has been preserved for future reference and can be searched by URL or content. Auto-tagged with domain and save date for easy discovery.", extractDomain(content));
            case "IMAGE" -> String.format("Visual content saved as %s. Image file stored locally with full resolution preserved. This visual resource contains graphics, photos, or diagrams that can be referenced later. File automatically organized by date and searchable by filename and visual characteristics.", content.length() > 50 ? content.substring(content.lastIndexOf("/") + 1) : content);
            case "TEXT" -> String.format("Text content saved: \"%s\". This written content includes notes, ideas, or important information preserved for future reference. Content is fully searchable and can be found by keywords, phrases, or topics mentioned within the text.", preview);
            default -> String.format("Digital content archived to TimeVault. Resource type: %s. Content preserved with metadata for easy retrieval through search. Automatically organized by type, date, and content for efficient discovery and reference.", type.toLowerCase());
        };
    }

    private String buildFallbackImageGallerySummary(String title, String notes, List<String> imageAnalyses) {
        String resolvedTitle = sanitize(title);
        String resolvedNotes = sanitize(notes);
        int imageCount = imageAnalyses == null ? 0 : imageAnalyses.size();

        if (imageAnalyses == null || imageAnalyses.isEmpty()) {
            return String.format(
                    "%s contains %d saved image%s. Shared notes: %s. The gallery can still be reviewed later even when AI synthesis is unavailable.",
                    resolvedTitle.isBlank() ? "This gallery" : resolvedTitle,
                    Math.max(imageCount, 1),
                    Math.max(imageCount, 1) == 1 ? "" : "s",
                    resolvedNotes.isBlank() ? "none provided" : truncate(resolvedNotes, 120));
        }

        List<String> selectedAnalyses = imageAnalyses.stream()
                .limit(3)
                .map(this::trimSentenceEnding)
                .filter(value -> !value.isBlank())
                .toList();

        String subjectSentence = String.format(
                "%s contains %d image%s focused on %s.",
                resolvedTitle.isBlank() ? "This gallery" : resolvedTitle,
                imageCount,
                imageCount == 1 ? "" : "s",
                selectedAnalyses.isEmpty() ? "the saved visual material" : selectedAnalyses.getFirst().toLowerCase(Locale.ROOT));

        String detailSentence = selectedAnalyses.size() > 1
                ? ensureSentenceEnding(String.join(" ", selectedAnalyses.subList(0, Math.min(selectedAnalyses.size(), 2))))
                : "The saved images include concrete visual details that can still be searched and reviewed later.";

        String usefulnessSentence = resolvedNotes.isBlank()
                ? "The gallery remains useful as a visual reference because it preserves the images together in one item."
                : "Shared notes add extra context: " + truncate(resolvedNotes, 140) + ".";

        return String.join(" ", subjectSentence, detailSentence, usefulnessSentence);
    }

    private String formatGalleryAnalyses(List<String> imageAnalyses) {
        if (imageAnalyses == null || imageAnalyses.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < imageAnalyses.size(); index++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("Image ").append(index + 1).append(": ").append(truncate(imageAnalyses.get(index), 500));
        }
        return builder.toString();
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

    private String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    public record UrlSummaryResult(VaultItem item, boolean aiGenerated) {
    }
}

