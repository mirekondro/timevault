package com.example.entities;

import com.example.support.DateFormats;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Archive {

    private Long id;
    private ArchiveType type;
    private String url;
    private String title;
    private String content;
    private String filePath;
    private String aiContext;
    private String sourcePlatform;
    private LocalDateTime createdAt;
    private List<String> tags = new ArrayList<>();

    public Archive() {
    }

    public Archive(
            Long id,
            ArchiveType type,
            String url,
            String title,
            String content,
            String filePath,
            String aiContext,
            String sourcePlatform,
            LocalDateTime createdAt,
            List<String> tags
    ) {
        this.id = id;
        this.type = type;
        this.url = url;
        this.title = title;
        this.content = content;
        this.filePath = filePath;
        this.aiContext = aiContext;
        this.sourcePlatform = sourcePlatform;
        this.createdAt = createdAt;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public String excerpt(int maxLength) {
        String clean = getContent().replaceAll("\\s+", " ").trim();
        if (clean.length() <= maxLength) {
            return clean;
        }
        return clean.substring(0, Math.max(0, maxLength - 1)).trim() + "...";
    }

    public String createdAtLabel() {
        if (createdAt == null) {
            return "Unknown date";
        }
        return createdAt.format(DateFormats.DISPLAY_DATE_TIME);
    }

    public boolean isRescuable() {
        return type == ArchiveType.URL && url != null && !url.isBlank() && !getSourcePlatform().contains("Wayback");
    }
}
