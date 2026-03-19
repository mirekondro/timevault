package com.example.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * SHARED MODEL - Used by both Web and Desktop versions
 *
 * Represents a saved item in the TimeVault (URL, Image, or Text)
 */
@Entity
@Table(name = "vault_items", indexes = {
        @Index(name = "idx_vault_items_created_at", columnList = "created_at"),
        @Index(name = "idx_vault_items_user_created_at", columnList = "user_id, created_at")
})
public class VaultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vault_items_user"))
    private VaultUser owner;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "content", columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Column(name = "ai_context", columnDefinition = "NVARCHAR(MAX)")
    private String aiContext;

    @Column(name = "item_type", length = 50)
    private String itemType; // URL, IMAGE, TEXT

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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

    public VaultUser getOwner() { return owner; }
    public void setOwner(VaultUser owner) { this.owner = owner; }

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
                ", ownerId=" + (owner == null ? null : owner.getId()) +
                ", title='" + title + '\'' +
                ", itemType='" + itemType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

