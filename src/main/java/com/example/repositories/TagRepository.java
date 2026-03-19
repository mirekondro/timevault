package com.example.repositories;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TagRepository {

    private final DatabaseManager databaseManager;

    public TagRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void replaceTags(long archiveId, List<String> tags) {
        Connection connection = null;
        try {
            connection = databaseManager.getConnection();
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM dbo.tags WHERE archive_id = ?")) {
                delete.setLong(1, archiveId);
                delete.executeUpdate();
            }
            if (tags != null && !tags.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO dbo.tags(archive_id, tag) VALUES (?, ?)")) {
                    for (String tag : tags) {
                        insert.setLong(1, archiveId);
                        insert.setString(2, tag);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
            connection.commit();
        } catch (SQLException exception) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw new IllegalStateException("Unable to store tags", exception);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<String> findTagsByArchiveId(long archiveId) {
        String sql = "SELECT tag FROM dbo.tags WHERE archive_id = ? ORDER BY tag";
        List<String> tags = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, archiveId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tags.add(resultSet.getString("tag"));
                }
            }
            return tags;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load tags", exception);
        }
    }
}
