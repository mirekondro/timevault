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
public class SchemaInitializer implements AppInitializer {

    private static final String LEGACY_USER_EMAIL = "legacy-import@timevault.local";

    private final ConnectionManager connectionManager;
    private final DatabaseConfig databaseConfig;
    private final DemoDataSeeder demoDataSeeder;

    public SchemaInitializer(ConnectionManager connectionManager, DatabaseConfig databaseConfig) {
        this.connectionManager = connectionManager;
        this.databaseConfig = databaseConfig;
        this.demoDataSeeder = new DemoDataSeeder();
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
            ensureVaultItemsLockColumns(statement);
            ensureVaultItemsDeletedColumn(statement);
            ensureVaultItemImagesTable(statement);
            migrateLegacyItems(connection);
            enforceUserOwnership(statement);
            ensureIndexes(statement);

            if (databaseConfig.resetOnStart()) {
                demoDataSeeder.seed(connection);
            }
        }
    }

    @Override
    public void initialize() throws SQLException {
        initializeSchema();
    }

    private void dropTables(Statement statement) throws SQLException {
        statement.executeUpdate("""
                IF OBJECT_ID('dbo.vault_item_images', 'U') IS NOT NULL
                DROP TABLE dbo.vault_item_images
                """);
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
                        is_locked BIT NOT NULL CONSTRAINT df_vault_items_is_locked DEFAULT 0,
                        lock_password_hash NVARCHAR(512) NULL,
                        lock_salt NVARCHAR(128) NULL,
                        lock_payload NVARCHAR(MAX) NULL,
                        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
                        updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
                        deleted_at DATETIME2 NULL,
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

    private void ensureVaultItemsLockColumns(Statement statement) throws SQLException {
        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_items', 'is_locked') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_items
                    ADD is_locked BIT NOT NULL CONSTRAINT df_vault_items_is_locked DEFAULT 0
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_items', 'lock_password_hash') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_items ADD lock_password_hash NVARCHAR(512) NULL
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_items', 'lock_salt') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_items ADD lock_salt NVARCHAR(128) NULL
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_items', 'lock_payload') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_items ADD lock_payload NVARCHAR(MAX) NULL
                END
                """);
    }

    private void ensureVaultItemsDeletedColumn(Statement statement) throws SQLException {
        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_items', 'deleted_at') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_items ADD deleted_at DATETIME2 NULL
                END
                """);
    }

    private void ensureVaultItemImagesTable(Statement statement) throws SQLException {
        statement.executeUpdate("""
                IF OBJECT_ID('dbo.vault_item_images', 'U') IS NOT NULL
                   AND COL_LENGTH('dbo.vault_item_images', 'id') IS NULL
                BEGIN
                    CREATE TABLE dbo.vault_item_images_v2 (
                        id BIGINT IDENTITY(1,1) PRIMARY KEY,
                        item_id BIGINT NOT NULL,
                        file_name NVARCHAR(500) NULL,
                        ai_context NVARCHAR(MAX) NULL,
                        mime_type NVARCHAR(100) NULL,
                        byte_count BIGINT NOT NULL CONSTRAINT df_vault_item_images_v2_byte_count DEFAULT 0,
                        display_order INT NOT NULL CONSTRAINT df_vault_item_images_v2_display_order DEFAULT 0,
                        protected_metadata NVARCHAR(MAX) NULL,
                        image_data VARBINARY(MAX) NULL,
                        protected_image_data VARBINARY(MAX) NULL,
                        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
                        updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
                        CONSTRAINT fk_vault_item_images_v2_item
                            FOREIGN KEY (item_id) REFERENCES dbo.vault_items(id) ON DELETE CASCADE
                    )

                    INSERT INTO dbo.vault_item_images_v2 (
                        item_id, file_name, ai_context, mime_type, byte_count, display_order,
                        protected_metadata, image_data, protected_image_data, created_at, updated_at
                    )
                    SELECT legacy.item_id,
                           CASE
                               WHEN i.item_type = 'IMAGE' THEN NULLIF(LTRIM(RTRIM(i.content)), '')
                               ELSE NULL
                           END,
                           CASE
                               WHEN i.item_type = 'IMAGE' THEN NULLIF(LTRIM(RTRIM(i.ai_context)), '')
                               ELSE NULL
                           END,
                           legacy.mime_type,
                           legacy.byte_count,
                           0,
                           NULL,
                           legacy.image_data,
                           legacy.protected_image_data,
                           legacy.created_at,
                           legacy.updated_at
                    FROM dbo.vault_item_images legacy
                    INNER JOIN dbo.vault_items i ON i.id = legacy.item_id

                    DROP TABLE dbo.vault_item_images
                    EXEC sp_rename 'dbo.vault_item_images_v2', 'vault_item_images'

                    UPDATE dbo.vault_items
                    SET content = NULL
                    WHERE item_type = 'IMAGE'
                END
                """);

        statement.executeUpdate("""
                IF OBJECT_ID('dbo.vault_item_images', 'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.vault_item_images (
                        id BIGINT IDENTITY(1,1) PRIMARY KEY,
                        item_id BIGINT NOT NULL,
                        file_name NVARCHAR(500) NULL,
                        ai_context NVARCHAR(MAX) NULL,
                        mime_type NVARCHAR(100) NULL,
                        byte_count BIGINT NOT NULL CONSTRAINT df_vault_item_images_byte_count DEFAULT 0,
                        display_order INT NOT NULL CONSTRAINT df_vault_item_images_display_order DEFAULT 0,
                        protected_metadata NVARCHAR(MAX) NULL,
                        image_data VARBINARY(MAX) NULL,
                        protected_image_data VARBINARY(MAX) NULL,
                        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
                        updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
                        CONSTRAINT fk_vault_item_images_item
                            FOREIGN KEY (item_id) REFERENCES dbo.vault_items(id) ON DELETE CASCADE
                    )
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_item_images', 'file_name') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_item_images ADD file_name NVARCHAR(500) NULL
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_item_images', 'ai_context') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_item_images ADD ai_context NVARCHAR(MAX) NULL
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_item_images', 'mime_type') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_item_images ADD mime_type NVARCHAR(100) NULL
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_item_images', 'byte_count') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_item_images
                    ADD byte_count BIGINT NOT NULL CONSTRAINT df_vault_item_images_byte_count DEFAULT 0
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_item_images', 'display_order') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_item_images
                    ADD display_order INT NOT NULL CONSTRAINT df_vault_item_images_display_order DEFAULT 0
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_item_images', 'protected_metadata') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_item_images ADD protected_metadata NVARCHAR(MAX) NULL
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_item_images', 'image_data') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_item_images ADD image_data VARBINARY(MAX) NULL
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_item_images', 'protected_image_data') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_item_images ADD protected_image_data VARBINARY(MAX) NULL
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_item_images', 'created_at') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_item_images
                    ADD created_at DATETIME2 NOT NULL CONSTRAINT df_vault_item_images_created_at DEFAULT SYSDATETIME()
                END
                """);

        statement.executeUpdate("""
                IF COL_LENGTH('dbo.vault_item_images', 'updated_at') IS NULL
                BEGIN
                    ALTER TABLE dbo.vault_item_images
                    ADD updated_at DATETIME2 NOT NULL CONSTRAINT df_vault_item_images_updated_at DEFAULT SYSDATETIME()
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

        statement.executeUpdate("""
                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.indexes
                    WHERE name = 'idx_vault_items_user_deleted_created_at'
                      AND object_id = OBJECT_ID('dbo.vault_items')
                )
                CREATE INDEX idx_vault_items_user_deleted_created_at
                    ON dbo.vault_items(user_id, deleted_at, created_at DESC)
                """);

        statement.executeUpdate("""
                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.indexes
                    WHERE name = 'idx_vault_item_images_item_id'
                      AND object_id = OBJECT_ID('dbo.vault_item_images')
                )
                CREATE INDEX idx_vault_item_images_item_id ON dbo.vault_item_images(item_id)
                """);

        statement.executeUpdate("""
                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.indexes
                    WHERE name = 'idx_vault_item_images_item_order'
                      AND object_id = OBJECT_ID('dbo.vault_item_images')
                )
                CREATE INDEX idx_vault_item_images_item_order ON dbo.vault_item_images(item_id, display_order, id)
                """);
    }
}
