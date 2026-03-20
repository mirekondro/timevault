package com.example.desktop.dao.api;

import com.example.desktop.dao.AppInitializer;

import java.sql.SQLException;

/**
 * Verifies that the Spring Boot backend API is reachable before the desktop UI starts using it.
 */
public class ApiBackendInitializer implements AppInitializer {

    private final TimeVaultApiClient apiClient;

    public ApiBackendInitializer(TimeVaultApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public void initialize() throws SQLException {
        apiClient.ping();
    }
}
