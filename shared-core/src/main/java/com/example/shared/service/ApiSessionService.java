package com.example.shared.service;

import com.example.shared.api.ApiSessionResponse;
import com.example.shared.model.UserSession;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lightweight token-based session store for the desktop-to-backend API.
 */
@Service
public class ApiSessionService {

    private static final Duration SESSION_TTL = Duration.ofHours(12);

    private final ConcurrentMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    public ApiSessionResponse createSession(UserSession userSession) {
        cleanupExpiredSessions();
        String token = UUID.randomUUID().toString();
        Instant issuedAt = Instant.now();
        sessions.put(token, new SessionEntry(userSession, issuedAt));
        return new ApiSessionResponse(token, userSession.id(), userSession.email(), issuedAt);
    }

    public Optional<UserSession> resolveSession(String token) {
        return resolveEntry(token).map(SessionEntry::userSession);
    }

    public Optional<ApiSessionResponse> getSession(String token) {
        return resolveEntry(token)
                .map(entry -> new ApiSessionResponse(token, entry.userSession().id(), entry.userSession().email(), entry.issuedAt()));
    }

    public ApiSessionResponse refreshSession(String token, UserSession userSession) {
        Instant issuedAt = Instant.now();
        sessions.put(token, new SessionEntry(userSession, issuedAt));
        return new ApiSessionResponse(token, userSession.id(), userSession.email(), issuedAt);
    }

    public void invalidate(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessions.remove(token);
    }

    private Optional<SessionEntry> resolveEntry(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        SessionEntry entry = sessions.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry)) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    private boolean isExpired(SessionEntry entry) {
        return entry.issuedAt().plus(SESSION_TTL).isBefore(Instant.now());
    }

    private void cleanupExpiredSessions() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().issuedAt().plus(SESSION_TTL).isBefore(now));
    }

    private record SessionEntry(UserSession userSession, Instant issuedAt) {
    }
}
