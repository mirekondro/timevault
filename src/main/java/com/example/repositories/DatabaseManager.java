package com.example.repositories;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final String jdbcUrl;

    public DatabaseManager(Path databasePath) {
        try {
            Files.createDirectories(databasePath.toAbsolutePath().getParent());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create database directory", exception);
        }
        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        initialize();
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private void initialize() {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS archives (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        type TEXT NOT NULL CHECK(type IN ('url','image','text','event')),
                        url TEXT,
                        title TEXT,
                        content TEXT,
                        file_path TEXT,
                        ai_context TEXT,
                        source_platform TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        archive_id INTEGER NOT NULL,
                        tag TEXT NOT NULL,
                        FOREIGN KEY (archive_id) REFERENCES archives(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS daily_capsules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        capsule_date TEXT UNIQUE,
                        headline TEXT,
                        vibe_summary TEXT,
                        trending_topics TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_archives_created_at ON archives(created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_tags_archive_id ON tags(archive_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_capsules_date ON daily_capsules(capsule_date DESC)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize SQLite schema", exception);
        }
    }
}
