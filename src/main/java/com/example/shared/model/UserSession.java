package com.example.shared.model;

/**
 * Safe authenticated session view shared by desktop and web frontends.
 */
public record UserSession(long id, String email) {
}
