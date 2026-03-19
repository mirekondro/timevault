package com.example.services;

import com.example.entities.Archive;
import com.example.entities.ArchiveDraft;
import com.example.entities.ArchiveType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RescueService {

    private static final DateTimeFormatter WAYBACK_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final HttpClient httpClient;
    private final FetchService fetchService;
    private final AiContextService aiContextService;
    private final ArchiveService archiveService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RescueService(
            HttpClient httpClient,
            FetchService fetchService,
            AiContextService aiContextService,
            ArchiveService archiveService
    ) {
        this.httpClient = httpClient;
        this.fetchService = fetchService;
        this.aiContextService = aiContextService;
        this.archiveService = archiveService;
    }

    public RescueResult rescue(String rawUrl) {
        String normalizedUrl = fetchService.normalizeUrl(rawUrl);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://archive.org/wayback/available?url=" + URLEncoder.encode(normalizedUrl, StandardCharsets.UTF_8)))
                    .GET()
                    .header("User-Agent", "TimeVault/1.0")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Wayback Machine returned HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode closest = root.path("archived_snapshots").path("closest");
            if (!closest.path("available").asBoolean(false)) {
                throw new IllegalStateException("No archived snapshot was found for this URL.");
            }

            String snapshotUrl = closest.path("url").asText();
            String timestamp = closest.path("timestamp").asText();
            LocalDateTime snapshotTime = parseTimestamp(timestamp);

            FetchedContent snapshot = fetchService.fetchPage(snapshotUrl);
            ArchiveDraft draft = archiveService.composeDraft(
                    ArchiveType.URL,
                    normalizedUrl,
                    snapshot.title(),
                    snapshot.content(),
                    "Wayback Machine",
                    snapshot.rawContent(),
                    null,
                    snapshotTime
            );
            draft.setAiContext(aiContextService.generateArchiveContext(snapshotTime, draft.getTitle(), draft.getContent()));

            return new RescueResult(normalizedUrl, snapshotUrl, snapshotTime, draft);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to rescue URL: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Wayback rescue was interrupted", exception);
        }
    }

    public Archive saveRescue(RescueResult rescueResult) {
        return archiveService.saveDraft(rescueResult.draft());
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        String normalized = timestamp == null ? "" : timestamp.trim();
        if (normalized.length() == 8) {
            normalized += "000000";
        }
        if (normalized.length() != 14) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(normalized, WAYBACK_TIMESTAMP);
    }
}
