package com.example.repositories;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final ConnectionManager connectionManager;

    public DatabaseManager() {
        this.connectionManager = new ConnectionManager();
        initialize();
    }

    public Connection getConnection() throws SQLException {
        return connectionManager.getConnection();
    }

    public String getDatabaseName() {
        return connectionManager.getProperty("db.database", "timevault");
    }

    private void initialize() {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            if (connectionManager.getBoolean("db.resetOnStart", true)) {
                statement.execute("DROP TABLE IF EXISTS dbo.tags");
                statement.execute("DROP TABLE IF EXISTS dbo.daily_capsules");
                statement.execute("DROP TABLE IF EXISTS dbo.archives");
            }

            statement.execute("""
                    IF OBJECT_ID('dbo.archives', 'U') IS NULL
                    CREATE TABLE dbo.archives (
                        id BIGINT IDENTITY(1,1) PRIMARY KEY,
                        type NVARCHAR(20) NOT NULL CHECK (type IN ('url','image','text','event')),
                        url NVARCHAR(2000) NULL,
                        title NVARCHAR(500) NULL,
                        content NVARCHAR(MAX) NULL,
                        file_path NVARCHAR(1000) NULL,
                        ai_context NVARCHAR(MAX) NULL,
                        source_platform NVARCHAR(255) NULL,
                        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
                    )
                    """);

            statement.execute("""
                    IF OBJECT_ID('dbo.tags', 'U') IS NULL
                    CREATE TABLE dbo.tags (
                        id BIGINT IDENTITY(1,1) PRIMARY KEY,
                        archive_id BIGINT NOT NULL,
                        tag NVARCHAR(100) NOT NULL,
                        CONSTRAINT fk_tags_archive FOREIGN KEY (archive_id) REFERENCES dbo.archives(id) ON DELETE CASCADE
                    )
                    """);

            statement.execute("""
                    IF OBJECT_ID('dbo.daily_capsules', 'U') IS NULL
                    CREATE TABLE dbo.daily_capsules (
                        id BIGINT IDENTITY(1,1) PRIMARY KEY,
                        capsule_date DATE NOT NULL UNIQUE,
                        headline NVARCHAR(1000) NULL,
                        vibe_summary NVARCHAR(MAX) NULL,
                        trending_topics NVARCHAR(1000) NULL,
                        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
                    )
                    """);

            statement.execute("""
                    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_archives_created_at' AND object_id = OBJECT_ID('dbo.archives'))
                    CREATE INDEX idx_archives_created_at ON dbo.archives(created_at DESC)
                    """);
            statement.execute("""
                    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_tags_archive_id' AND object_id = OBJECT_ID('dbo.tags'))
                    CREATE INDEX idx_tags_archive_id ON dbo.tags(archive_id)
                    """);
            statement.execute("""
                    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_capsules_date' AND object_id = OBJECT_ID('dbo.daily_capsules'))
                    CREATE INDEX idx_capsules_date ON dbo.daily_capsules(capsule_date DESC)
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize SQL Server schema", exception);
        }
    }
}
