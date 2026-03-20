package com.example.shared.api;

/**
 * Login/register payload for the desktop-facing backend API.
 */
public record ApiAuthRequest(String email, String password) {
}
