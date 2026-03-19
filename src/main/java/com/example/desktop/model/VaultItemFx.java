package com.example.desktop.model;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Observable desktop model for one archived vault item.
 */
public class VaultItemFx {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final LongProperty id = new SimpleLongProperty();
    private final StringProperty title = new SimpleStringProperty("");
    private final StringProperty content = new SimpleStringProperty("");
    private final StringProperty aiContext = new SimpleStringProperty("");
    private final StringProperty itemType = new SimpleStringProperty("");
    private final StringProperty tags = new SimpleStringProperty("");
    private final StringProperty sourceUrl = new SimpleStringProperty("");
    private final ObjectProperty<LocalDateTime> createdAt = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> updatedAt = new SimpleObjectProperty<>();

    public long getId() {
        return id.get();
    }

    public void setId(long value) {
        id.set(value);
    }

    public LongProperty idProperty() {
        return id;
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

    public String getFormattedCreatedAt() {
        LocalDateTime timestamp = getCreatedAt();
        return timestamp == null ? "Unknown time" : timestamp.format(DISPLAY_FORMAT);
    }

    public String getDisplaySnippet() {
        String source = firstNonBlank(getAiContext(), getContent(), "No preview available yet.");
        return source.length() > 180 ? source.substring(0, 180) + "..." : source;
    }

    public String getDisplayContext() {
        return firstNonBlank(getAiContext(), "No context stored yet.");
    }

    public String getDisplayContent() {
        return firstNonBlank(getContent(), "No content stored yet.");
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
