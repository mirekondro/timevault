package com.example.repositories;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerException;

import java.io.InputStream;
import java.sql.Connection;
import java.util.Properties;

public class ConnectionManager {

    private final Properties properties = new Properties();

    public ConnectionManager() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("db.properties was not found in src/main/resources.");
            }
            properties.load(inputStream);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load database configuration.", exception);
        }
    }

    public Connection getConnection() throws SQLServerException {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName(getRequired("db.server"));
        dataSource.setPortNumber(Integer.parseInt(getRequired("db.port")));
        dataSource.setDatabaseName(getRequired("db.database"));
        dataSource.setUser(getRequired("db.user"));
        dataSource.setPassword(getRequired("db.password"));
        dataSource.setTrustServerCertificate(getBoolean("db.trustServerCertificate", true));
        return dataSource.getConnection();
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(defaultValue)));
    }

    private String getRequired(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required database property: " + key);
        }
        return value.trim();
    }
}
