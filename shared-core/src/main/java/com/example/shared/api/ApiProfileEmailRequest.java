package com.example.shared.api;

/**
 * Profile email update payload for the desktop-facing API.
 */
public record ApiProfileEmailRequest(String newEmail, String currentPassword) {
}
