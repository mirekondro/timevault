package com.example.web.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ensures the backend database supports multiple images per vault item.
 */
@Component
public class VaultImageSchemaMigration implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(VaultImageSchemaMigration.class);

    private final DataSource dataSource;

    public VaultImageSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureVaultItemImagesTable(statement);
                connection.commit();
                LOGGER.info("Verified multi-image schema for vault_item_images.");
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
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

        statement.executeUpdate("""
                IF OBJECT_ID('dbo.vault_item_images', 'U') IS NOT NULL
                BEGIN
                    DECLARE @dropUniqueConstraints NVARCHAR(MAX) = N'';
                    SELECT @dropUniqueConstraints = @dropUniqueConstraints
                        + N'ALTER TABLE dbo.vault_item_images DROP CONSTRAINT [' + kc.name + N'];'
                    FROM sys.key_constraints kc
                    INNER JOIN sys.index_columns ic
                        ON kc.parent_object_id = ic.object_id
                       AND kc.unique_index_id = ic.index_id
                    INNER JOIN sys.columns c
                        ON c.object_id = ic.object_id
                       AND c.column_id = ic.column_id
                    WHERE kc.parent_object_id = OBJECT_ID('dbo.vault_item_images')
                      AND kc.type = 'UQ'
                    GROUP BY kc.name
                    HAVING COUNT(*) = 1
                       AND MAX(c.name) = 'item_id';

                    IF LEN(@dropUniqueConstraints) > 0
                    BEGIN
                        EXEC sp_executesql @dropUniqueConstraints;
                    END
                END
                """);

        statement.executeUpdate("""
                IF OBJECT_ID('dbo.vault_item_images', 'U') IS NOT NULL
                BEGIN
                    DECLARE @dropUniqueIndexes NVARCHAR(MAX) = N'';
                    SELECT @dropUniqueIndexes = @dropUniqueIndexes
                        + N'DROP INDEX [' + i.name + N'] ON dbo.vault_item_images;'
                    FROM sys.indexes i
                    INNER JOIN sys.index_columns ic
                        ON i.object_id = ic.object_id
                       AND i.index_id = ic.index_id
                    INNER JOIN sys.columns c
                        ON c.object_id = ic.object_id
                       AND c.column_id = ic.column_id
                    WHERE i.object_id = OBJECT_ID('dbo.vault_item_images')
                      AND i.is_unique = 1
                      AND i.is_primary_key = 0
                      AND i.is_unique_constraint = 0
                    GROUP BY i.name
                    HAVING COUNT(*) = 1
                       AND MAX(c.name) = 'item_id';

                    IF LEN(@dropUniqueIndexes) > 0
                    BEGIN
                        EXEC sp_executesql @dropUniqueIndexes;
                    END
                END
                """);

        statement.executeUpdate("""
                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.indexes
                    WHERE name = 'idx_vault_item_images_item_order'
                      AND object_id = OBJECT_ID('dbo.vault_item_images')
                )
                BEGIN
                    CREATE INDEX idx_vault_item_images_item_order
                    ON dbo.vault_item_images (item_id, display_order, id)
                END
                """);
    }
}
