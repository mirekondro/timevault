package com.example.desktop.dao;

import com.example.shared.model.VaultUser;

import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO contract for desktop user accounts.
 */
public interface UserDAO {

    Optional<VaultUser> findById(long id) throws SQLException;

    Optional<VaultUser> findByEmail(String email) throws SQLException;

    VaultUser insert(VaultUser user) throws SQLException;

    boolean updateEmail(long userId, String email) throws SQLException;

    boolean updatePasswordHash(long userId, String passwordHash) throws SQLException;
}
