package com.example.desktop.dao.api;

import com.example.desktop.dao.UserDAO;
import com.example.shared.api.ApiAuthRequest;
import com.example.shared.api.ApiMessageResponse;
import com.example.shared.api.ApiProfileEmailRequest;
import com.example.shared.api.ApiProfilePasswordRequest;
import com.example.shared.api.ApiSessionResponse;
import com.example.shared.model.UserSession;

import java.sql.SQLException;

/**
 * API-backed desktop user account DAO.
 */
public class ApiUserDAO implements UserDAO {

    private final TimeVaultApiClient apiClient;

    public ApiUserDAO(TimeVaultApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public UserSession register(String email, String rawPassword) throws SQLException {
        ApiSessionResponse sessionResponse = apiClient.post(
                "/api/client/auth/register",
                new ApiAuthRequest(email, rawPassword),
                ApiSessionResponse.class,
                false);
        apiClient.setSessionToken(sessionResponse.token());
        return toSession(sessionResponse);
    }

    @Override
    public UserSession authenticate(String email, String rawPassword) throws SQLException {
        ApiSessionResponse sessionResponse = apiClient.post(
                "/api/client/auth/login",
                new ApiAuthRequest(email, rawPassword),
                ApiSessionResponse.class,
                false);
        apiClient.setSessionToken(sessionResponse.token());
        return toSession(sessionResponse);
    }

    @Override
    public UserSession updateEmail(long userId, String email, String currentPassword) throws SQLException {
        ApiSessionResponse sessionResponse = apiClient.patch(
                "/api/client/profile/email",
                new ApiProfileEmailRequest(email, currentPassword),
                ApiSessionResponse.class,
                true);
        apiClient.setSessionToken(sessionResponse.token());
        return toSession(sessionResponse);
    }

    @Override
    public void updatePassword(long userId, String currentPassword, String newPassword, String confirmPassword) throws SQLException {
        apiClient.patch(
                "/api/client/profile/password",
                new ApiProfilePasswordRequest(currentPassword, newPassword, confirmPassword),
                ApiMessageResponse.class,
                true);
    }

    @Override
    public void logout() throws SQLException {
        try {
            if (apiClient.hasSessionToken()) {
                apiClient.post("/api/client/auth/logout", null, ApiMessageResponse.class, true);
            }
        } finally {
            apiClient.clearSessionToken();
        }
    }

    private UserSession toSession(ApiSessionResponse sessionResponse) {
        return new UserSession(sessionResponse.userId(), sessionResponse.email());
    }
}
