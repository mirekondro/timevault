package com.example.desktop.dao;

import com.example.shared.security.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Ensures the desktop database schema exists before the GUI starts using it.
 */
public class SchemaInitializer {

    private static final String LEGACY_USER_EMAIL = "legacy-import@timevault.local";

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
                dropTables(statement);
            }

            ensureUsersTable(statement);
            ensureVaultItemsTable(statement);
            ensureVaultItemsUserColumn(statement);
            migrateLegacyItems(connection);
            enforceUserOwnership(statement);
            ensureIndexes(statement);
        }
    }

    private void dropTables(Statement statement) throws SQLException {
        statement.executeUpdate("""
                IF OBJECT_ID('dbo.vault_items', 'U') IS NOT NULL
                DROP TABLE dbo.vault_items
                """);
        statement.executeUpdate("""
                IF OBJECT_ID('dbo.vault_users', 'U') IS NOT NULL
                DROP TABLE dbo.vault_users
                """);
    }

    private void ensureUsersTable(Statement statement) throws SQLException {
        statement.executeUpdate("""
                IF OBJECT_ID('dbo.vault_users', 'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.vault_users (
                        id BIGINT IDENTITY(1,1) PRIMARY KEY,
                        email NVARCHAR(320) NOT NULL,
                        password_hash NVARCHAR(512) NOT NULL,
                        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
                        updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
                    )
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_users', 'password_hash') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_users ADD password_hash NVARCHAR(512) NULL
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_users', 'created_at') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_users ADD created_at DATETIME2 NOT NULL CONSTRAINT df_vault_users_created_at DEFAULT SYSDATETIME()
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_users', 'updated_at') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_users ADD updated_at DATETIME2 NOT NULL CONSTRAINT df_vault_users_updated_at DEFAULT SYSDATETIME()
                END
                """);
    }

    private void ensureVaultItemsTable(Statement statement) throws SQLException {
        statement.executeUpdate("""
                IF OBJECT_ID('dbo.vault_items', 'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.vault_items (
                        id BIGINT IDENTITY(1,1) PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        title NVARCHAR(500) NOT NULL,
                        content NVARCHAR(MAX) NULL,
                        ai_context NVARCHAR(MAX) NULL,
                        item_type NVARCHAR(50) NULL,
                        tags NVARCHAR(500) NULL,
                        source_url NVARCHAR(1000) NULL,
                        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
                        updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
                        CONSTRAINT fk_vault_items_user FOREIGN KEY (user_id) REFERENCES dbo.vault_users(id)
                    )
                END
                """);
    }

    private void ensureVaultItemsUserColumn(Statement statement) throws SQLException {
        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_items', 'user_id') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_items ADD user_id BIGINT NULL
                END
                """);
    }

    private void migrateLegacyItems(Connection connection) throws SQLException {
        if (!hasLegacyItems(connection)) {
            return;
        }

        long legacyUserId = ensureLegacyUser(connection);
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE dbo.vault_items
                SET user_id = ?
                WHERE user_id IS NULL
                """)) {
            update.setLong(1, legacyUserId);
            update.executeUpdate();
        }
    }

    private boolean hasLegacyItems(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT TOP 1 1
                FROM dbo.vault_items
                WHERE user_id IS NULL
                """);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
        }
    }

    private long ensureLegacyUser(Connection connection) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id
                FROM dbo.vault_users
                WHERE email = ?
                """)) {
            select.setString(1, LEGACY_USER_EMAIL);
            try (ResultSet resultSet = select.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("id");
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO dbo.vault_users (email, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, LEGACY_USER_EMAIL);
            insert.setString(2, PasswordHasher.hash("legacy-account-disabled"));
            insert.setTimestamp(3, Timestamp.valueOf(now));
            insert.setTimestamp(4, Timestamp.valueOf(now));
            insert.executeUpdate();

            try (ResultSet generatedKeys = insert.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
        }

        throw new SQLException("Could not create the legacy migration account.");
    }

    private void enforceUserOwnership(Statement statement) throws SQLException {
        statement.executeUpdate("""
                IF EXISTS (
                    SELECT 1
                    FROM sys.columns
                    WHERE object_id = OBJECT_ID('dbo.vault_items')
                      AND name = 'user_id'
                      AND is_nullable = 1
                )
                BEGIN
                    ALTER TABLE dbo.vault_items ALTER COLUMN user_id BIGINT NOT NULL
                END
                """);

        statement.executeUpdate("""
                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.foreign_keys
                    WHERE name = 'fk_vault_items_user'
                      AND parent_object_id = OBJECT_ID('dbo.vault_items')
                )
                BEGIN
                    ALTER TABLE dbo.vault_items
                    WITH CHECK ADD CONSTRAINT fk_vault_items_user
                    FOREIGN KEY (user_id) REFERENCES dbo.vault_users(id)
                END
                """);
    }

    private void ensureIndexes(Statement statement) throws SQLException {
        statement.executeUpdate("""
                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.indexes
                    WHERE name = 'ux_vault_users_email'
                      AND object_id = OBJECT_ID('dbo.vault_users')
                )
                CREATE UNIQUE INDEX ux_vault_users_email ON dbo.vault_users(email)
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

        statement.executeUpdate("""
                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.indexes
                    WHERE name = 'idx_vault_items_user_created_at'
                      AND object_id = OBJECT_ID('dbo.vault_items')
                )
                CREATE INDEX idx_vault_items_user_created_at ON dbo.vault_items(user_id, created_at DESC)
                """);
    }
}
