package com.example.repositories;

import com.example.entities.Archive;
import com.example.entities.ArchiveType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ArchiveRepository {

    private final DatabaseManager databaseManager;

    public ArchiveRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Archive save(Archive archive) {
        String sql = """
                INSERT INTO dbo.archives(type, url, title, content, file_path, ai_context, source_platform, created_at)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, archive.getType().databaseValue());
            statement.setString(2, archive.getUrl());
            statement.setString(3, archive.getTitle());
            statement.setString(4, archive.getContent());
            statement.setString(5, archive.getFilePath());
            statement.setString(6, archive.getAiContext());
            statement.setString(7, archive.getSourcePlatform());
            statement.setTimestamp(8, Timestamp.valueOf(archive.getCreatedAt()));
            try (ResultSet keys = statement.executeQuery()) {
                if (!keys.next()) {
                    throw new IllegalStateException("SQL Server did not return a generated archive id.");
                }
                archive.setId(keys.getLong(1));
            }
            return archive;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save archive", exception);
        }
    }

    public Optional<Archive> findById(long id) {
        String sql = "SELECT * FROM dbo.archives WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(map(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load archive", exception);
        }
    }

    public List<Archive> findRecent(int limit) {
        String sql = """
                SELECT * FROM dbo.archives
                ORDER BY created_at DESC
                OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                return readAll(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load recent archives", exception);
        }
    }

    public List<Archive> search(String query, ArchiveType filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT a.* FROM dbo.archives a
                LEFT JOIN dbo.tags t ON t.archive_id = a.id
                WHERE 1 = 1
                """);
        List<String> parameters = new ArrayList<>();

        if (filter != null) {
            sql.append(" AND a.type = ?");
            parameters.add(filter.databaseValue());
        }

        if (query != null && !query.isBlank()) {
            sql.append("""
                     AND (
                        LOWER(COALESCE(a.title, '')) LIKE ?
                        OR LOWER(COALESCE(a.content, '')) LIKE ?
                        OR LOWER(COALESCE(a.url, '')) LIKE ?
                        OR LOWER(COALESCE(t.tag, '')) LIKE ?
                     )
                    """);
            String pattern = "%" + query.toLowerCase() + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }

        sql.append(" ORDER BY a.created_at DESC");

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setString(index + 1, parameters.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return readAll(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to search archives", exception);
        }
    }

    public void delete(long archiveId) {
        String sql = "DELETE FROM dbo.archives WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, archiveId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete archive", exception);
        }
    }

    public long countAll() {
        return singleCount("SELECT COUNT(*) FROM dbo.archives");
    }

    public long countSince(LocalDateTime since) {
        String sql = "SELECT COUNT(*) FROM dbo.archives WHERE created_at >= ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(since));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to calculate recent archive statistics", exception);
        }
    }

    public long countRescued() {
        return singleCount("SELECT COUNT(*) FROM dbo.archives WHERE LOWER(COALESCE(source_platform, '')) LIKE '%wayback%'");
    }

    private long singleCount(String sql, String... parameters) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to calculate archive statistics", exception);
        }
    }

    private List<Archive> readAll(ResultSet resultSet) throws SQLException {
        List<Archive> archives = new ArrayList<>();
        while (resultSet.next()) {
            archives.add(map(resultSet));
        }
        return archives;
    }

    private Archive map(ResultSet resultSet) throws SQLException {
        return new Archive(
                resultSet.getLong("id"),
                ArchiveType.fromDatabaseValue(resultSet.getString("type")),
                resultSet.getString("url"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("file_path"),
                resultSet.getString("ai_context"),
                resultSet.getString("source_platform"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                List.of()
        );
    }
}
