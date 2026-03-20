package com.example.desktop.dao.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Small HTTP client for the desktop app to call the Spring Boot backend.
 */
public class TimeVaultApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private volatile String sessionToken = "";

    public TimeVaultApiClient(String baseUrl, int connectTimeoutSeconds) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                .build();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public void ping() throws SQLException {
        get("/api/vault/health", JsonNode.class, false);
    }

    public boolean hasSessionToken() {
        return sessionToken != null && !sessionToken.isBlank();
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken == null ? "" : sessionToken.trim();
    }

    public void clearSessionToken() {
        this.sessionToken = "";
    }

    public <T> T get(String path, Class<T> responseType, boolean authorized) throws SQLException {
        return send("GET", path, null, responseType, null, authorized);
    }

    public <T> T get(String path, TypeReference<T> responseType, boolean authorized) throws SQLException {
        return send("GET", path, null, null, responseType, authorized);
    }

    public <T> T post(String path, Object requestBody, Class<T> responseType, boolean authorized) throws SQLException {
        return send("POST", path, requestBody, responseType, null, authorized);
    }

    public <T> T put(String path, Object requestBody, Class<T> responseType, boolean authorized) throws SQLException {
        return send("PUT", path, requestBody, responseType, null, authorized);
    }

    public <T> T patch(String path, Object requestBody, Class<T> responseType, boolean authorized) throws SQLException {
        return send("PATCH", path, requestBody, responseType, null, authorized);
    }

    public <T> T delete(String path, Class<T> responseType, boolean authorized) throws SQLException {
        return send("DELETE", path, null, responseType, null, authorized);
    }

    private <T> T send(String method,
                       String path,
                       Object requestBody,
                       Class<T> responseType,
                       TypeReference<T> typeReference,
                       boolean authorized) throws SQLException {
        HttpRequest request = buildRequest(method, path, requestBody, authorized);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = response.statusCode();
            String body = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                if (responseType == null && typeReference == null) {
                    return null;
                }
                if (responseType == Void.class || body == null || body.isBlank()) {
                    return null;
                }
                if (responseType != null) {
                    return objectMapper.readValue(body, responseType);
                }
                return objectMapper.readValue(body, typeReference);
            }

            String errorMessage = extractErrorMessage(body);
            if (statusCode == 404) {
                throw new ApiNotFoundException(errorMessage);
            }
            if (statusCode == 400 || statusCode == 401 || statusCode == 403) {
                throw new IllegalArgumentException(errorMessage);
            }
            throw new SQLException(errorMessage);
        } catch (ConnectException exception) {
            throw new SQLException("Could not reach the TimeVault backend at " + baseUrl + ".", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("The request to the TimeVault backend was interrupted.", exception);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Could not read the response from the TimeVault backend.", exception);
        } catch (IOException exception) {
            throw new SQLException("Could not communicate with the TimeVault backend.", exception);
        }
    }

    private HttpRequest buildRequest(String method, String path, Object requestBody, boolean authorized) throws SQLException {
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
        boolean hasJsonBody = requestBody != null;
        if (hasJsonBody) {
            try {
                bodyPublisher = HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8);
            } catch (JsonProcessingException exception) {
                throw new SQLException("Could not serialize the API request body.", exception);
            }
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(resolveUri(path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");

        if (hasJsonBody) {
            builder.header("Content-Type", "application/json");
        }
        if (authorized) {
            if (!hasSessionToken()) {
                throw new IllegalArgumentException("You need to log in before using the API.");
            }
            builder.header("Authorization", "Bearer " + sessionToken);
        }

        return builder.method(method, bodyPublisher).build();
    }

    private URI resolveUri(String path) throws SQLException {
        String normalizedPath = path == null || path.isBlank() ? "/" : path.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }

        try {
            return new URI(baseUrl + normalizedPath);
        } catch (URISyntaxException exception) {
            throw new SQLException("The API URL is not valid: " + baseUrl + normalizedPath, exception);
        }
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "The TimeVault backend returned an empty error response.";
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.hasNonNull("message")) {
                return root.get("message").asText();
            }
            if (root.hasNonNull("error")) {
                return root.get("error").asText();
            }
        } catch (IOException ignored) {
            // Fall through to raw body.
        }

        return body.trim();
    }

    private String normalizeBaseUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "http://localhost:8081";
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
