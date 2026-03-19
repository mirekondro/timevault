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
        if (!isConfigured()) {
            return createFallbackUrlSummary(url, pageContent);
        }

        try {
            String prompt = """
                Read this webpage content and create a clear, informative summary. Provide your response in this EXACT format:
                
                TITLE: [Generate a clear, descriptive title - NO URLs, just describe what it is]
                DESCRIPTION: [Provide exactly 3 sentences that summarize the actual CONTENT/INFORMATION, not the page itself]
                
                Focus on WHAT THE CONTENT SAYS, not what type of page it is:
                
                For NEWS: Summarize the main story, key facts, and important details
                For TUTORIALS: Explain what you'll learn and the main steps/concepts covered
                For ARTICLES: Summarize the main arguments, insights, or information presented
                For DOCUMENTATION: Explain the key features, functions, or concepts documented
                For RESEARCH: Summarize the findings, methodology, or conclusions
                For PRODUCTS: Describe what the product does, key features, and benefits
                
                Write as if you're telling someone what you just read, not describing the webpage.
                
                Examples:
                - Instead of: "This news article discusses Apple's latest announcement"
                - Write: "Apple announced new iPhone 15 models featuring titanium bodies, improved cameras with 5x optical zoom, and USB-C connectivity replacing Lightning ports"
                
                - Instead of: "This tutorial covers React development"
                - Write: "Learn to build React components using hooks, manage application state with useState and useEffect, and create interactive user interfaces with event handling"
                
                URL: %s
                Content:
                %s
                """.formatted(url, truncate(pageContent, 3000));

            String response = callGemini(prompt, null);
            return parseUrlSummaryResponse(response, url, pageContent);
        } catch (Exception e) {
            return createFallbackUrlSummary(url, pageContent);
        }
    }

    private VaultItem parseUrlSummaryResponse(String response, String url, String pageContent) {
        try {
            String[] lines = response.split("\n");
            String title = "Saved Webpage";
            String description = response;

            for (String line : lines) {
                if (line.startsWith("TITLE:")) {
                    title = line.substring(6).trim();
                } else if (line.startsWith("DESCRIPTION:")) {
                    description = line.substring(12).trim();
                    // Get the rest of the description if it spans multiple lines
                    int index = response.indexOf("DESCRIPTION:") + 12;
                    description = response.substring(index).trim();
                    break;
                }
            }

            VaultItem item = new VaultItem();
            item.setTitle(title);
            item.setAiContext(description);
            item.setSourceUrl(url);
            item.setContent(truncate(pageContent, 5000)); // Store more content for search
            return item;
        } catch (Exception e) {
            return createFallbackUrlSummary(url, pageContent);
        }
    }

    private VaultItem createFallbackUrlSummary(String url, String pageContent) {
        VaultItem item = new VaultItem();

        // Generate a simple title from URL
        String title = generateTitleFromUrl(url);

        // Generate a descriptive fallback
        String description = String.format(
            "This webpage from %s contains saved content including text, images, and interactive elements. " +
            "The page has been archived locally for offline access and future reference. " +
            "Content is searchable by keywords and can be found using the site name or topic mentioned within the page.",
            extractDomain(url)
        );

        item.setTitle(title);
        item.setAiContext(description);
        item.setSourceUrl(url);
        item.setContent(truncate(pageContent, 5000));
        return item;
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

