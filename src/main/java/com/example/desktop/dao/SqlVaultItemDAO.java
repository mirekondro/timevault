package com.example.desktop.dao;

import com.example.desktop.model.VaultItemFx;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
                SELECT id, user_id, title, content, ai_context, item_type, tags, source_url,
                       is_locked, lock_password_hash, lock_salt, lock_payload, created_at, updated_at
                FROM dbo.vault_items
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
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
    public VaultItemFx insert(long userId, VaultItemFx item) throws SQLException {
        String sql = """
                INSERT INTO dbo.vault_items (
                    user_id, title, content, ai_context, item_type, tags, source_url,
                    is_locked, lock_password_hash, lock_salt, lock_payload, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        LocalDateTime createdAt = item.getCreatedAt() == null ? LocalDateTime.now() : item.getCreatedAt();
        LocalDateTime updatedAt = item.getUpdatedAt() == null ? createdAt : item.getUpdatedAt();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

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

        item.setOwnerId(userId);
        item.setCreatedAt(createdAt);
        item.setUpdatedAt(updatedAt);
        return item;
    }

    @Override
    public boolean update(long userId, VaultItemFx item) throws SQLException {
        String sql = """
                UPDATE dbo.vault_items
                SET title = ?, content = ?, ai_context = ?, item_type = ?, tags = ?, source_url = ?,
                    is_locked = ?, lock_password_hash = ?, lock_salt = ?, lock_payload = ?, updated_at = ?
                WHERE id = ? AND user_id = ?
                """;

        LocalDateTime updatedAt = item.getUpdatedAt() == null ? LocalDateTime.now() : item.getUpdatedAt();
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
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

            boolean updated = statement.executeUpdate() > 0;
            if (updated) {
                item.setOwnerId(userId);
                item.setUpdatedAt(updatedAt);
            }
            return updated;
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

        Timestamp createdAt = resultSet.getTimestamp("created_at");
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        item.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        item.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return item;
    }
}
