package com.example.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Optional binary/image payload attached to a vault item.
 */
@Entity
@Table(name = "vault_item_images")
public class VaultItemImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    @JsonIgnore
    private VaultItem item;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "ai_context", columnDefinition = "NVARCHAR(MAX)")
    private String aiContext;

    @Column(name = "protected_metadata", columnDefinition = "NVARCHAR(MAX)")
    @JsonIgnore
    private String protectedMetadata;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "byte_count", nullable = false)
    private long byteCount;

    @jakarta.persistence.Lob
    @Column(name = "image_data")
    @JsonIgnore
    private byte[] imageData;

    @jakarta.persistence.Lob
    @Column(name = "protected_image_data")
    @JsonIgnore
    private byte[] protectedImageData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public VaultItem getItem() {
        return item;
    }

    public void setItem(VaultItem item) {
        this.item = item;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getAiContext() {
        return aiContext;
    }

    public void setAiContext(String aiContext) {
        this.aiContext = aiContext;
    }

    public String getProtectedMetadata() {
        return protectedMetadata;
    }

    public void setProtectedMetadata(String protectedMetadata) {
        this.protectedMetadata = protectedMetadata;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = Math.max(displayOrder, 0);
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getByteCount() {
        return byteCount;
    }

    public void setByteCount(long byteCount) {
        this.byteCount = byteCount;
    }

    public byte[] getImageData() {
        return imageData;
    }

    public void setImageData(byte[] imageData) {
        this.imageData = imageData;
    }

    public byte[] getProtectedImageData() {
        return protectedImageData;
    }

    public void setProtectedImageData(byte[] protectedImageData) {
        this.protectedImageData = protectedImageData;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
