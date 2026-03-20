package com.example.desktop.model;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Observable desktop model for one archived vault item.
 */
public class VaultItemFx {

    public static final String LOCKED_TITLE_PLACEHOLDER = "Locked item";

    private final LongProperty id = new SimpleLongProperty();
    private final LongProperty ownerId = new SimpleLongProperty();
    private final StringProperty title = new SimpleStringProperty("");
    private final StringProperty content = new SimpleStringProperty("");
    private final StringProperty aiContext = new SimpleStringProperty("");
    private final StringProperty itemType = new SimpleStringProperty("");
    private final StringProperty tags = new SimpleStringProperty("");
    private final StringProperty sourceUrl = new SimpleStringProperty("");
    private final ObjectProperty<LocalDateTime> createdAt = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> updatedAt = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> deletedAt = new SimpleObjectProperty<>();
    private boolean locked;
    private String lockPasswordHash = "";
    private String lockSalt = "";
    private String lockPayload = "";
    private UnlockedItemSession unlockedSession;
    private List<GalleryImageFx> galleryImages = new ArrayList<>();
    private String imageMimeType = "";
    private long imageByteCount;
    private byte[] cachedImageBytes = new byte[0];

    public long getId() {
        return id.get();
    }

    public void setId(long value) {
        id.set(value);
    }

    public LongProperty idProperty() {
        return id;
    }

    public long getOwnerId() {
        return ownerId.get();
    }

    public void setOwnerId(long value) {
        ownerId.set(value);
    }

    public LongProperty ownerIdProperty() {
        return ownerId;
    }

    public String getTitle() {
        return title.get();
    }

    public void setTitle(String value) {
        title.set(value);
    }

    public StringProperty titleProperty() {
        return title;
    }

    public String getContent() {
        return content.get();
    }

    public void setContent(String value) {
        content.set(value);
    }

    public StringProperty contentProperty() {
        return content;
    }

    public String getAiContext() {
        return aiContext.get();
    }

    public void setAiContext(String value) {
        aiContext.set(value);
    }

    public StringProperty aiContextProperty() {
        return aiContext;
    }

    public String getItemType() {
        return itemType.get();
    }

    public void setItemType(String value) {
        itemType.set(value);
    }

    public StringProperty itemTypeProperty() {
        return itemType;
    }

    public String getTags() {
        return tags.get();
    }

    public void setTags(String value) {
        tags.set(value);
    }

    public StringProperty tagsProperty() {
        return tags;
    }

    public String getSourceUrl() {
        return sourceUrl.get();
    }

    public void setSourceUrl(String value) {
        sourceUrl.set(value);
    }

    public StringProperty sourceUrlProperty() {
        return sourceUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt.get();
    }

    public void setCreatedAt(LocalDateTime value) {
        createdAt.set(value);
    }

    public ObjectProperty<LocalDateTime> createdAtProperty() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt.get();
    }

    public void setUpdatedAt(LocalDateTime value) {
        updatedAt.set(value);
    }

    public ObjectProperty<LocalDateTime> updatedAtProperty() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt.get();
    }

    public void setDeletedAt(LocalDateTime value) {
        deletedAt.set(value);
    }

    public ObjectProperty<LocalDateTime> deletedAtProperty() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getLockPasswordHash() {
        return lockPasswordHash;
    }

    public void setLockPasswordHash(String lockPasswordHash) {
        this.lockPasswordHash = lockPasswordHash == null ? "" : lockPasswordHash;
    }

    public String getLockSalt() {
        return lockSalt;
    }

    public void setLockSalt(String lockSalt) {
        this.lockSalt = lockSalt == null ? "" : lockSalt;
    }

    public String getLockPayload() {
        return lockPayload;
    }

    public void setLockPayload(String lockPayload) {
        this.lockPayload = lockPayload == null ? "" : lockPayload;
    }

    public boolean isUnlockedInSession() {
        return unlockedSession != null;
    }

    public UnlockedItemSession getUnlockedSession() {
        return unlockedSession;
    }

    public void setUnlockedSession(UnlockedItemSession unlockedSession) {
        this.unlockedSession = unlockedSession;
    }

    public void clearUnlockedSession() {
        unlockedSession = null;
    }

    public List<GalleryImageFx> getGalleryImages() {
        return galleryImages.stream()
                .map(GalleryImageFx::copy)
                .toList();
    }

    public void setGalleryImages(List<GalleryImageFx> galleryImages) {
        this.galleryImages = galleryImages == null ? new ArrayList<>() : galleryImages.stream()
                .map(GalleryImageFx::copy)
                .sorted(java.util.Comparator.comparingInt(GalleryImageFx::getDisplayOrder)
                        .thenComparingLong(GalleryImageFx::getId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        syncPrimaryImageState();
    }

    public void clearGalleryImages() {
        galleryImages.clear();
        syncPrimaryImageState();
    }

    public int getImageCount() {
        return galleryImages.size();
    }

    public boolean hasStoredImages() {
        return !galleryImages.isEmpty();
    }

    public GalleryImageFx getPrimaryImage() {
        return galleryImages.isEmpty() ? null : galleryImages.getFirst().copy();
    }

    public String getImageMimeType() {
        return imageMimeType;
    }

    public void setImageMimeType(String imageMimeType) {
        this.imageMimeType = imageMimeType == null ? "" : imageMimeType;
        if (!galleryImages.isEmpty()) {
            galleryImages.getFirst().setMimeType(this.imageMimeType);
        }
    }

    public long getImageByteCount() {
        return imageByteCount;
    }

    public void setImageByteCount(long imageByteCount) {
        this.imageByteCount = Math.max(imageByteCount, 0L);
        if (!galleryImages.isEmpty()) {
            galleryImages.getFirst().setByteCount(this.imageByteCount);
        }
    }

    public boolean hasStoredImage() {
        return hasStoredImages() || imageByteCount > 0L;
    }

    public byte[] getCachedImageBytes() {
        if (!galleryImages.isEmpty()) {
            return galleryImages.getFirst().getCachedImageBytes();
        }
        return cachedImageBytes.clone();
    }

    public void setCachedImageBytes(byte[] cachedImageBytes) {
        this.cachedImageBytes = cachedImageBytes == null ? new byte[0] : cachedImageBytes.clone();
        if (!galleryImages.isEmpty()) {
            galleryImages.getFirst().setCachedImageBytes(this.cachedImageBytes);
        }
    }

    public void clearCachedImageBytes() {
        cachedImageBytes = new byte[0];
        for (GalleryImageFx galleryImage : galleryImages) {
            galleryImage.clearCachedImageBytes();
        }
    }

    public String getPreviewSource() {
        return firstNonBlank(getAiContext(), getContent(), "");
    }

    public String getContextSource() {
        return firstNonBlank(getAiContext(), "");
    }

    public String getContentSource() {
        return firstNonBlank(getContent(), "");
    }

    private void syncPrimaryImageState() {
        if (galleryImages.isEmpty()) {
            imageMimeType = "";
            imageByteCount = 0L;
            cachedImageBytes = new byte[0];
            return;
        }

        GalleryImageFx primaryImage = galleryImages.getFirst();
        imageMimeType = primaryImage.getMimeType();
        imageByteCount = primaryImage.getByteCount();
        cachedImageBytes = primaryImage.getCachedImageBytes();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
