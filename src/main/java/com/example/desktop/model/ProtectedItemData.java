package com.example.desktop.model;

/**
 * Sensitive vault item fields that are encrypted when item-level locking is enabled.
 */
public record ProtectedItemData(
        String title,
        String content,
        String aiContext,
        String tags,
        String sourceUrl) {
}
