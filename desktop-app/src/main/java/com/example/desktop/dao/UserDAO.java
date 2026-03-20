package com.example.desktop.dao;

import com.example.shared.model.UserSession;

import java.sql.SQLException;

/**
 * DAO contract for desktop user accounts.
 */
public interface UserDAO {

    UserSession register(String email, String rawPassword) throws SQLException;

    UserSession authenticate(String email, String rawPassword) throws SQLException;

    UserSession updateEmail(long userId, String email, String currentPassword) throws SQLException;

    void updatePassword(long userId, String currentPassword, String newPassword, String confirmPassword) throws SQLException;

    void logout() throws SQLException;
}
