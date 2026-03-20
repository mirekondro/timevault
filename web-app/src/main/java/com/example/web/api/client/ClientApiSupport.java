package com.example.web.api.client;

import com.example.shared.model.UserSession;
import com.example.shared.service.ApiSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared auth-header/session handling for client API controllers.
 */
abstract class ClientApiSupport {

    private final ApiSessionService apiSessionService;

    protected ClientApiSupport(ApiSessionService apiSessionService) {
        this.apiSessionService = apiSessionService;
    }

    protected ApiSessionService apiSessionService() {
        return apiSessionService;
    }

    protected String requireBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is required.");
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required.");
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required.");
        }
        return token;
    }

    protected UserSession requireSession(String token) {
        return apiSessionService.resolveSession(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session is invalid or expired."));
    }
}
