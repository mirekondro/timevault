package com.example.shared.api;

import java.time.Instant;

/**
 * Issued API session token plus the authenticated user identity.
 */
public record ApiSessionResponse(String token, long userId, String email, Instant issuedAt) {
}
