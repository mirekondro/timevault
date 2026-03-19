package com.example.shared.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * SHARED MODEL - Used by both Web and Desktop versions
 *
 * Represents a saved item in the TimeVault (URL, Image, or Text)
 */
@Entity
@Table(name = "vault_items")
public class VaultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String aiContext;

    @Column(length = 50)
    private String itemType; // URL, IMAGE, TEXT

    @Column(length = 500)
    private String tags;

    @Column(length = 1000)
    private String sourceUrl;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @Column(name = "user_id", nullable = false)
    private Long userId = 1L; // Default user ID for single-user application

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (userId == null) {
            userId = 1L; // Ensure user_id is always set
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public VaultItem() {}

    public VaultItem(String title, String content, String itemType) {
        this.title = title;
        this.content = content;
        this.itemType = itemType;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAiContext() { return aiContext; }
    public void setAiContext(String aiContext) { this.aiContext = aiContext; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    @Override
    public String toString() {
        return "VaultItem{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", itemType='" + itemType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

