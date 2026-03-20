package com.example.desktop.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads shared non-database application settings for the desktop app.
 */
public class DesktopApplicationConfig {

    private final Properties properties = new Properties();

    public DesktopApplicationConfig() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load application.properties.", exception);
        }
    }

    public String geminiApiKey() {
        return properties.getProperty("gemini.api.key", "").trim();
    }

    public String geminiModel() {
        return properties.getProperty("gemini.model", "gemini-2.5-flash").trim();
    }

    public String backendMode() {
        return properties.getProperty("desktop.backend.mode", "api").trim().toLowerCase();
    }

    public String apiBaseUrl() {
        return properties.getProperty("timevault.api.baseUrl", "http://localhost:8081").trim();
    }

    public int apiConnectTimeoutSeconds() {
        String value = properties.getProperty("timevault.api.connectTimeoutSeconds", "10").trim();
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return 10;
        }
    }
}
