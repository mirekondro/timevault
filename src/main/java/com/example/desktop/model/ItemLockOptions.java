package com.example.desktop.model;

/**
 * Requested lock configuration supplied by add/edit dialogs.
 */
public record ItemLockOptions(
        boolean enabled,
        String password,
        String confirmPassword) {
}
