package com.example.desktop.model;

/**
 * Immutable UI notification shown in the shared toast overlay.
 */
public record ToastNotification(long id, String message, ToastNotificationType type) {
}
