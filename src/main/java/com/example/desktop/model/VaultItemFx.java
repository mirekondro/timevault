package com.example.desktop.model;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDateTime;

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
    private boolean locked;
    private String lockPasswordHash = "";
    private String lockSalt = "";
    private String lockPayload = "";
    private UnlockedItemSession unlockedSession;

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

    public String getPreviewSource() {
        return firstNonBlank(getAiContext(), getContent(), "");
    }

    public String getContextSource() {
        return firstNonBlank(getAiContext(), "");
    }

    public String getContentSource() {
        return firstNonBlank(getContent(), "");
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
