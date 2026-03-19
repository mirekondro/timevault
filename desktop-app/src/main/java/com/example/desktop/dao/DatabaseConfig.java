package com.example.desktop.dao;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads local database settings from the ignored db.properties resource.
 */
public class DatabaseConfig {

    private final Properties properties = new Properties();

    public DatabaseConfig() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("db.properties was not found on the classpath.");
            }
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load db.properties.", exception);
        }
    }

    public String server() {
        return required("db.server");
    }

    public int port() {
        return Integer.parseInt(required("db.port"));
    }

    public String database() {
        return required("db.database");
    }

    public String user() {
        return required("db.user");
    }

    public String password() {
        return required("db.password");
    }

    public boolean trustServerCertificate() {
        return Boolean.parseBoolean(properties.getProperty("db.trustServerCertificate", "true"));
    }

    public boolean resetOnStart() {
        return Boolean.parseBoolean(properties.getProperty("db.resetOnStart", "false"));
    }

    private String required(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required database property: " + key);
        }
        return value.trim();
    }
}
