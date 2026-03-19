package com.example.repositories;

import com.example.entities.DailyCapsule;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CapsuleRepository {

    private final DatabaseManager databaseManager;

    public CapsuleRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public DailyCapsule saveOrUpdate(DailyCapsule capsule) {
        Optional<DailyCapsule> existing = findByDate(capsule.getCapsuleDate());
        if (existing.isPresent()) {
            update(capsule, existing.get().getId());
            capsule.setId(existing.get().getId());
            return capsule;
        }
        return insert(capsule);
    }

    public Optional<DailyCapsule> findByDate(LocalDate date) {
        String sql = "SELECT * FROM dbo.daily_capsules WHERE capsule_date = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(date));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(map(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load daily capsule", exception);
        }
    }

    public List<DailyCapsule> findAll() {
        String sql = "SELECT * FROM dbo.daily_capsules ORDER BY capsule_date DESC";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<DailyCapsule> capsules = new ArrayList<>();
            while (resultSet.next()) {
                capsules.add(map(resultSet));
            }
            return capsules;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load daily capsules", exception);
        }
    }

    private DailyCapsule insert(DailyCapsule capsule) {
        String sql = """
                INSERT INTO dbo.daily_capsules(capsule_date, headline, vibe_summary, trending_topics, created_at)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(capsule.getCapsuleDate()));
            statement.setString(2, capsule.getHeadline());
            statement.setString(3, capsule.getVibeSummary());
            statement.setString(4, capsule.getTrendingTopics());
            statement.setTimestamp(5, Timestamp.valueOf(capsule.getCreatedAt()));
            try (ResultSet keys = statement.executeQuery()) {
                if (!keys.next()) {
                    throw new IllegalStateException("SQL Server did not return a generated capsule id.");
                }
                capsule.setId(keys.getLong(1));
            }
            return capsule;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save daily capsule", exception);
        }
    }

    private void update(DailyCapsule capsule, long id) {
        String sql = """
                UPDATE dbo.daily_capsules
                SET headline = ?, vibe_summary = ?, trending_topics = ?, created_at = ?
                WHERE id = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, capsule.getHeadline());
            statement.setString(2, capsule.getVibeSummary());
            statement.setString(3, capsule.getTrendingTopics());
            statement.setTimestamp(4, Timestamp.valueOf(capsule.getCreatedAt()));
            statement.setLong(5, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update daily capsule", exception);
        }
    }

    private DailyCapsule map(ResultSet resultSet) throws SQLException {
        return new DailyCapsule(
                resultSet.getLong("id"),
                resultSet.getDate("capsule_date").toLocalDate(),
                resultSet.getString("headline"),
                resultSet.getString("vibe_summary"),
                resultSet.getString("trending_topics"),
                resultSet.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
