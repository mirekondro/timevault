package com.example.desktop.dao;

import com.example.shared.model.UserSession;
import com.example.shared.model.VaultUser;
import com.example.shared.security.PasswordHasher;

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
    public UserSession register(String email, String rawPassword) throws SQLException {
        if (findUserByEmail(email).isPresent()) {
            throw new IllegalArgumentException("An account with that email already exists.");
        }

        VaultUser savedUser = insertUser(new VaultUser(email, PasswordHasher.hash(rawPassword)));
        return toSession(savedUser);
    }

    @Override
    public UserSession authenticate(String email, String rawPassword) throws SQLException {
        Optional<VaultUser> existingUser = findUserByEmail(email);
        if (existingUser.isEmpty()) {
            throw new IllegalArgumentException("No account was found for that email.");
        }
        if (!PasswordHasher.matches(rawPassword, existingUser.get().getPasswordHash())) {
            throw new IllegalArgumentException("Email or password is incorrect.");
        }
        return toSession(existingUser.get());
    }

    @Override
    public UserSession updateEmail(long userId, String email, String currentPassword) throws SQLException {
        Optional<VaultUser> currentUser = findUserById(userId);
        if (currentUser.isEmpty()) {
            throw new IllegalArgumentException("Account not found.");
        }

        VaultUser user = currentUser.get();
        if (!PasswordHasher.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (user.getEmail().equalsIgnoreCase(email)) {
            throw new IllegalArgumentException("New email matches the current email.");
        }

        Optional<VaultUser> existingUser = findUserByEmail(email);
        if (existingUser.isPresent() && existingUser.get().getId() != userId) {
            throw new IllegalArgumentException("An account with that email already exists.");
        }

        boolean updated = updateEmailInternal(userId, email);
        if (!updated) {
            throw new IllegalArgumentException("Account not found.");
        }

        return new UserSession(userId, email);
    }

    @Override
    public void updatePassword(long userId, String currentPassword, String newPassword, String confirmPassword) throws SQLException {
        Optional<VaultUser> currentUser = findUserById(userId);
        if (currentUser.isEmpty()) {
            throw new IllegalArgumentException("Account not found.");
        }
        if (!PasswordHasher.matches(currentPassword, currentUser.get().getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirmation do not match.");
        }

        boolean updated = updatePasswordHashInternal(userId, PasswordHasher.hash(newPassword));
        if (!updated) {
            throw new IllegalArgumentException("Account not found.");
        }
    }

    @Override
    public void logout() {
        // No server-side session to invalidate in SQL mode.
    }

    private Optional<VaultUser> findUserById(long id) throws SQLException {
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

    private Optional<VaultUser> findUserByEmail(String email) throws SQLException {
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

    private VaultUser insertUser(VaultUser user) throws SQLException {
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

    private boolean updateEmailInternal(long userId, String email) throws SQLException {
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

    private boolean updatePasswordHashInternal(long userId, String passwordHash) throws SQLException {
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

    private UserSession toSession(VaultUser user) {
        return new UserSession(user.getId(), user.getEmail());
    }
}
