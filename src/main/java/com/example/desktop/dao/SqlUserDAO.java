package com.example.desktop.dao;

import com.example.shared.model.VaultUser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * SQL Server implementation of desktop user account storage.
 */
public class SqlUserDAO implements UserDAO {

    private final ConnectionManager connectionManager;

    public SqlUserDAO(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Optional<VaultUser> findById(long id) throws SQLException {
        String sql = """
                SELECT id, email, password_hash, created_at, updated_at
                FROM dbo.vault_users
                WHERE id = ?
                """;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    @Override
    public Optional<VaultUser> findByEmail(String email) throws SQLException {
        String sql = """
                SELECT id, email, password_hash, created_at, updated_at
                FROM dbo.vault_users
                WHERE email = ?
                """;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    @Override
    public VaultUser insert(VaultUser user) throws SQLException {
        String sql = """
                INSERT INTO dbo.vault_users (email, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """;

        LocalDateTime createdAt = user.getCreatedAt() == null ? LocalDateTime.now() : user.getCreatedAt();
        LocalDateTime updatedAt = user.getUpdatedAt() == null ? createdAt : user.getUpdatedAt();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getEmail());
            statement.setString(2, user.getPasswordHash());
            statement.setTimestamp(3, Timestamp.valueOf(createdAt));
            statement.setTimestamp(4, Timestamp.valueOf(updatedAt));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    user.setId(generatedKeys.getLong(1));
                }
            }
        }

        user.setCreatedAt(createdAt);
        user.setUpdatedAt(updatedAt);
        return user;
    }

    @Override
    public boolean updateEmail(long userId, String email) throws SQLException {
        String sql = """
                UPDATE dbo.vault_users
                SET email = ?, updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setLong(3, userId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updatePasswordHash(long userId, String passwordHash) throws SQLException {
        String sql = """
                UPDATE dbo.vault_users
                SET password_hash = ?, updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setLong(3, userId);
            return statement.executeUpdate() > 0;
        }
    }

    private VaultUser mapRow(ResultSet resultSet) throws SQLException {
        VaultUser user = new VaultUser();
        user.setId(resultSet.getLong("id"));
        user.setEmail(resultSet.getString("email"));
        user.setPasswordHash(resultSet.getString("password_hash"));

        Timestamp createdAt = resultSet.getTimestamp("created_at");
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        user.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        user.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return user;
    }
}
