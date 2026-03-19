package com.example.entities;

public enum ArchiveType {
    URL("url", "URL"),
    IMAGE("image", "Image"),
    TEXT("text", "Text"),
    EVENT("event", "Event");

    private final String databaseValue;
    private final String displayName;

    ArchiveType(String databaseValue, String displayName) {
        this.databaseValue = databaseValue;
        this.displayName = displayName;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public String displayName() {
        return displayName;
    }

    public static ArchiveType fromDatabaseValue(String value) {
        for (ArchiveType type : values()) {
            if (type.databaseValue.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return TEXT;
    }
}
