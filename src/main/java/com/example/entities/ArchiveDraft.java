package com.example.entities;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ArchiveDraft {

    private ArchiveType type;
    private String url;
    private String title;
    private String content;
    private String aiContext;
    private String sourcePlatform;
    private List<String> tags = new ArrayList<>();
    private String rawPayload;
    private Path sourceFile;
    private LocalDateTime createdAt = LocalDateTime.now();

    public ArchiveType getType() {
        return type;
    }

    public void setType(ArchiveType type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title == null || title.isBlank() ? "Untitled capture" : title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAiContext() {
        return aiContext == null ? "" : aiContext;
    }

    public void setAiContext(String aiContext) {
        this.aiContext = aiContext;
    }

    public String getSourcePlatform() {
        return sourcePlatform == null ? "Local capture" : sourcePlatform;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(Path sourceFile) {
        this.sourceFile = sourceFile;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
