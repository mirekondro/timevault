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
    public List<VaultItemFx> findAll() throws SQLException {
        String sql = """
                SELECT id, title, content, ai_context, item_type, tags, source_url, created_at, updated_at
                FROM dbo.vault_items
                ORDER BY created_at DESC, id DESC
                """;

        List<VaultItemFx> items = new ArrayList<>();
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                items.add(mapRow(resultSet));
            }
        }
        return items;
    }

    @Override
    public VaultItemFx insert(VaultItemFx item) throws SQLException {
        String sql = """
                INSERT INTO dbo.vault_items (title, content, ai_context, item_type, tags, source_url, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        LocalDateTime createdAt = item.getCreatedAt() == null ? LocalDateTime.now() : item.getCreatedAt();
        LocalDateTime updatedAt = item.getUpdatedAt() == null ? createdAt : item.getUpdatedAt();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, item.getTitle());
            statement.setString(2, item.getContent());
            statement.setString(3, item.getAiContext());
            statement.setString(4, item.getItemType());
            statement.setString(5, item.getTags());
            statement.setString(6, item.getSourceUrl());
            statement.setTimestamp(7, Timestamp.valueOf(createdAt));
            statement.setTimestamp(8, Timestamp.valueOf(updatedAt));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    item.setId(generatedKeys.getLong(1));
                }
            }
        }

        item.setCreatedAt(createdAt);
        item.setUpdatedAt(updatedAt);
        return item;
    }

    @Override
    public void deleteById(long itemId) throws SQLException {
        String sql = "DELETE FROM dbo.vault_items WHERE id = ?";
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, itemId);
            statement.executeUpdate();
        }
    }

    private VaultItemFx mapRow(ResultSet resultSet) throws SQLException {
        VaultItemFx item = new VaultItemFx();
        item.setId(resultSet.getLong("id"));
        item.setTitle(resultSet.getString("title"));
        item.setContent(resultSet.getString("content"));
        item.setAiContext(resultSet.getString("ai_context"));
        item.setItemType(resultSet.getString("item_type"));
        item.setTags(resultSet.getString("tags"));
        item.setSourceUrl(resultSet.getString("source_url"));

        Timestamp createdAt = resultSet.getTimestamp("created_at");
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        item.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        item.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return item;
    }
}
