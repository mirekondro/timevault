package com.example.shared.api;

/**
 * Profile password update payload for the desktop-facing API.
 */
public record ApiProfilePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {
}
