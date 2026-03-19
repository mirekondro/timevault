package com.example.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class FetchService {

    private final HttpClient httpClient;

    public FetchService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public FetchedContent fetchPage(String rawUrl) {
        String normalizedUrl = normalizeUrl(rawUrl);
        HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedUrl))
                .GET()
                .header("User-Agent", "TimeVault/1.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("The page responded with HTTP " + response.statusCode());
            }

            Document document = Jsoup.parse(response.body(), response.uri().toString());
            document.select("script, style, noscript, svg").remove();
            Element mainContent = document.selectFirst("article, main, body");
            String extractedText = mainContent != null ? mainContent.text() : document.text();
            extractedText = extractedText.replaceAll("\\s+", " ").trim();

            String title = document.title();
            if (title == null || title.isBlank()) {
                title = hostLabel(response.uri().toString());
            }

            String content = extractedText.isBlank() ? "No readable text could be extracted from this page." : extractedText;
            if (content.length() > 20000) {
                content = content.substring(0, 20000);
            }

            return new FetchedContent(
                    normalizedUrl,
                    response.uri().toString(),
                    title,
                    content,
                    response.body(),
                    hostLabel(response.uri().toString())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fetch " + normalizedUrl + ": " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fetching was interrupted", exception);
        }
    }

    public String normalizeUrl(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("A URL is required.");
        }
        if (!trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    private String hostLabel(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null || host.isBlank()) {
                return "internet";
            }
            return host.replaceFirst("^www\\.", "");
        } catch (URISyntaxException exception) {
            return "internet";
        }
    }
}
