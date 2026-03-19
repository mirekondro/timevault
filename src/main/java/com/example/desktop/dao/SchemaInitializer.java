package com.example.desktop.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ensures the desktop database schema exists before the GUI starts using it.
 */
public class SchemaInitializer {

    private final ConnectionManager connectionManager;
    private final DatabaseConfig databaseConfig;

    public SchemaInitializer(ConnectionManager connectionManager, DatabaseConfig databaseConfig) {
        this.connectionManager = connectionManager;
        this.databaseConfig = databaseConfig;
    }

    public void initializeSchema() throws SQLException {
        try (Connection connection = connectionManager.getConnection();
             Statement statement = connection.createStatement()) {

            if (databaseConfig.resetOnStart()) {
                statement.executeUpdate("""
                        IF OBJECT_ID('dbo.vault_items', 'U') IS NOT NULL
                        DROP TABLE dbo.vault_items
                        """);
            }

            statement.executeUpdate("""
                    IF OBJECT_ID('dbo.vault_items', 'U') IS NULL
                    BEGIN
                        CREATE TABLE dbo.vault_items (
                            id BIGINT IDENTITY(1,1) PRIMARY KEY,
                            title NVARCHAR(500) NOT NULL,
                            content NVARCHAR(MAX) NULL,
                            ai_context NVARCHAR(MAX) NULL,
                            item_type NVARCHAR(50) NULL,
                            tags NVARCHAR(500) NULL,
                            source_url NVARCHAR(1000) NULL,
                            created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
                            updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
                        )
                    END
                    """);

            statement.executeUpdate("""
                    IF NOT EXISTS (
                        SELECT 1
                        FROM sys.indexes
                        WHERE name = 'idx_vault_items_created_at'
                          AND object_id = OBJECT_ID('dbo.vault_items')
                    )
                    CREATE INDEX idx_vault_items_created_at ON dbo.vault_items(created_at DESC)
                    """);
        }
    }
}
