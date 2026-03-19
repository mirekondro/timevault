package com.example.desktop.dao;

import com.example.desktop.model.StoredImageRecord;
import com.example.desktop.model.VaultItemFx;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQL Server implementation of the desktop DAO.
 */
public class SqlVaultItemDAO implements VaultItemDAO {

    private final ConnectionManager connectionManager;

    public SqlVaultItemDAO(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public List<VaultItemFx> findAllByUserId(long userId) throws SQLException {
        String sql = """
                SELECT i.id, i.user_id, i.title, i.content, i.ai_context, i.item_type, i.tags, i.source_url,
                       i.is_locked, i.lock_password_hash, i.lock_salt, i.lock_payload, i.created_at, i.updated_at,
                       img.mime_type AS image_mime_type,
                       img.byte_count AS image_byte_count
                FROM dbo.vault_items i
                LEFT JOIN dbo.vault_item_images img ON img.item_id = i.id
                WHERE i.user_id = ?
                ORDER BY i.created_at DESC, i.id DESC
                """;

        List<VaultItemFx> items = new ArrayList<>();
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    items.add(mapRow(resultSet));
                }
            }
        }
        return items;
    }

    @Override
    public VaultItemFx insert(long userId, VaultItemFx item, StoredImageRecord imageRecord) throws SQLException {
        String sql = """
                INSERT INTO dbo.vault_items (
                    user_id, title, content, ai_context, item_type, tags, source_url,
                    is_locked, lock_password_hash, lock_salt, lock_payload, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        LocalDateTime createdAt = item.getCreatedAt() == null ? LocalDateTime.now() : item.getCreatedAt();
        LocalDateTime updatedAt = item.getUpdatedAt() == null ? createdAt : item.getUpdatedAt();

        try (Connection connection = connectionManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, userId);
                    statement.setString(2, item.getTitle());
                    statement.setString(3, item.getContent());
                    statement.setString(4, item.getAiContext());
                    statement.setString(5, item.getItemType());
                    statement.setString(6, item.getTags());
                    statement.setString(7, item.getSourceUrl());
                    statement.setBoolean(8, item.isLocked());
                    statement.setString(9, item.getLockPasswordHash());
                    statement.setString(10, item.getLockSalt());
                    statement.setString(11, item.getLockPayload());
                    statement.setTimestamp(12, Timestamp.valueOf(createdAt));
                    statement.setTimestamp(13, Timestamp.valueOf(updatedAt));
                    statement.executeUpdate();

                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            item.setId(generatedKeys.getLong(1));
                        }
                    }
                }

                syncImageRecord(connection, item.getId(), imageRecord, createdAt, updatedAt);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }

        item.setOwnerId(userId);
        item.setCreatedAt(createdAt);
        item.setUpdatedAt(updatedAt);
        applyImageMetadata(item, imageRecord);
        return item;
    }

    @Override
    public boolean update(long userId, VaultItemFx item, StoredImageRecord imageRecord) throws SQLException {
        String sql = """
                UPDATE dbo.vault_items
                SET title = ?, content = ?, ai_context = ?, item_type = ?, tags = ?, source_url = ?,
                    is_locked = ?, lock_password_hash = ?, lock_salt = ?, lock_payload = ?, updated_at = ?
                WHERE id = ? AND user_id = ?
                """;

        LocalDateTime updatedAt = item.getUpdatedAt() == null ? LocalDateTime.now() : item.getUpdatedAt();

        try (Connection connection = connectionManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean updated;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, item.getTitle());
                    statement.setString(2, item.getContent());
                    statement.setString(3, item.getAiContext());
                    statement.setString(4, item.getItemType());
                    statement.setString(5, item.getTags());
                    statement.setString(6, item.getSourceUrl());
                    statement.setBoolean(7, item.isLocked());
                    statement.setString(8, item.getLockPasswordHash());
                    statement.setString(9, item.getLockSalt());
                    statement.setString(10, item.getLockPayload());
                    statement.setTimestamp(11, Timestamp.valueOf(updatedAt));
                    statement.setLong(12, item.getId());
                    statement.setLong(13, userId);
                    updated = statement.executeUpdate() > 0;
                }

                if (updated) {
                    syncImageRecord(connection, item.getId(), imageRecord, item.getCreatedAt(), updatedAt);
                    connection.commit();
                    item.setOwnerId(userId);
                    item.setUpdatedAt(updatedAt);
                    applyImageMetadata(item, imageRecord);
                } else {
                    connection.rollback();
                }
                return updated;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    @Override
    public boolean deleteById(long userId, long itemId) throws SQLException {
        String sql = "DELETE FROM dbo.vault_items WHERE id = ? AND user_id = ?";
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, itemId);
            statement.setLong(2, userId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<StoredImageRecord> findStoredImageByItemId(long userId, long itemId) throws SQLException {
        String sql = """
                SELECT img.mime_type, img.byte_count, img.image_data, img.protected_image_data
                FROM dbo.vault_item_images img
                INNER JOIN dbo.vault_items i ON i.id = img.item_id
                WHERE i.user_id = ? AND img.item_id = ?
                """;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, itemId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(new StoredImageRecord(
                        resultSet.getString("mime_type"),
                        resultSet.getLong("byte_count"),
                        resultSet.getBytes("image_data"),
                        resultSet.getBytes("protected_image_data")));
            }
        }
    }

    private void syncImageRecord(Connection connection,
                                 long itemId,
                                 StoredImageRecord imageRecord,
                                 LocalDateTime createdAt,
                                 LocalDateTime updatedAt) throws SQLException {
        if (imageRecord == null || imageRecord.byteCount() <= 0) {
            deleteImageRecord(connection, itemId);
            return;
        }

        String sql = """
                MERGE dbo.vault_item_images AS target
                USING (SELECT ? AS item_id) AS source
                ON target.item_id = source.item_id
                WHEN MATCHED THEN
                    UPDATE SET mime_type = ?, byte_count = ?, image_data = ?, protected_image_data = ?, updated_at = ?
                WHEN NOT MATCHED THEN
                    INSERT (item_id, mime_type, byte_count, image_data, protected_image_data, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?);
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, itemId);
            statement.setString(2, imageRecord.mimeType());
            statement.setLong(3, imageRecord.byteCount());
            setNullableBytes(statement, 4, imageRecord.imageData());
            setNullableBytes(statement, 5, imageRecord.protectedImageData());
            statement.setTimestamp(6, Timestamp.valueOf(updatedAt));
            statement.setLong(7, itemId);
            statement.setString(8, imageRecord.mimeType());
            statement.setLong(9, imageRecord.byteCount());
            setNullableBytes(statement, 10, imageRecord.imageData());
            setNullableBytes(statement, 11, imageRecord.protectedImageData());
            statement.setTimestamp(12, Timestamp.valueOf(createdAt == null ? updatedAt : createdAt));
            statement.setTimestamp(13, Timestamp.valueOf(updatedAt));
            statement.executeUpdate();
        }
    }

    private void deleteImageRecord(Connection connection, long itemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM dbo.vault_item_images WHERE item_id = ?")) {
            statement.setLong(1, itemId);
            statement.executeUpdate();
        }
    }

    private void setNullableBytes(PreparedStatement statement, int parameterIndex, byte[] value) throws SQLException {
        if (value == null || value.length == 0) {
            statement.setNull(parameterIndex, Types.VARBINARY);
            return;
        }
        statement.setBytes(parameterIndex, value);
    }

    private void applyImageMetadata(VaultItemFx item, StoredImageRecord imageRecord) {
        if (item == null) {
            return;
        }
        if (imageRecord == null) {
            item.setImageMimeType("");
            item.setImageByteCount(0L);
            item.clearCachedImageBytes();
            return;
        }

        item.setImageMimeType(imageRecord.mimeType());
        item.setImageByteCount(imageRecord.byteCount());
    }

    private VaultItemFx mapRow(ResultSet resultSet) throws SQLException {
        VaultItemFx item = new VaultItemFx();
        item.setId(resultSet.getLong("id"));
        item.setOwnerId(resultSet.getLong("user_id"));
        item.setTitle(resultSet.getString("title"));
        item.setContent(resultSet.getString("content"));
        item.setAiContext(resultSet.getString("ai_context"));
        item.setItemType(resultSet.getString("item_type"));
        item.setTags(resultSet.getString("tags"));
        item.setSourceUrl(resultSet.getString("source_url"));
        item.setLocked(resultSet.getBoolean("is_locked"));
        item.setLockPasswordHash(resultSet.getString("lock_password_hash"));
        item.setLockSalt(resultSet.getString("lock_salt"));
        item.setLockPayload(resultSet.getString("lock_payload"));
        item.setImageMimeType(resultSet.getString("image_mime_type"));
        item.setImageByteCount(resultSet.getLong("image_byte_count"));

        Timestamp createdAt = resultSet.getTimestamp("created_at");
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        item.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        item.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return item;
    }
}
