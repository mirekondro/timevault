package com.example.shared.service;

import com.example.shared.model.UserSession;
import com.example.shared.model.VaultUser;
import com.example.shared.repository.VaultUserRepository;
import com.example.shared.security.AccountValidator;
import com.example.shared.security.PasswordHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared registration and login service for the web frontend.
 */
@Service
@Transactional
public class AuthService {

    private final VaultUserRepository userRepository;

    @Autowired
    public AuthService(VaultUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserSession register(String email, String rawPassword) {
        String normalizedEmail = AccountValidator.normalizeEmail(email);
        validateEmail(normalizedEmail);
        validatePassword(rawPassword);

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("An account with that email already exists.");
        }

        VaultUser user = new VaultUser(normalizedEmail, PasswordHasher.hash(rawPassword));
        VaultUser savedUser = userRepository.save(user);
        return toSession(savedUser);
    }

    public UserSession authenticate(String email, String rawPassword) {
        String normalizedEmail = AccountValidator.normalizeEmail(email);
        validateEmail(normalizedEmail);
        validatePassword(rawPassword);

        VaultUser user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("No account was found for that email."));

        if (!PasswordHasher.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Email or password is incorrect.");
        }

        return toSession(user);
    }

    public UserSession requireUser(long userId) {
        VaultUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        return toSession(user);
    }

    public UserSession updateEmail(long userId, String newEmail, String currentPassword) {
        String normalizedEmail = AccountValidator.normalizeEmail(newEmail);
        validateEmail(normalizedEmail);
        validatePassword(currentPassword);

        VaultUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));

        if (!PasswordHasher.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (normalizedEmail.equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("New email matches the current email.");
        }

        userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(existingUser -> !existingUser.getId().equals(userId))
                .ifPresent(existingUser -> {
                    throw new IllegalArgumentException("An account with that email already exists.");
                });

        user.setEmail(normalizedEmail);
        VaultUser savedUser = userRepository.save(user);
        return toSession(savedUser);
    }

    public void updatePassword(long userId,
                               String currentPassword,
                               String newPassword,
                               String confirmPassword) {
        validatePassword(currentPassword);
        validatePassword(newPassword);

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirmation do not match.");
        }

        VaultUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));

        if (!PasswordHasher.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        user.setPasswordHash(PasswordHasher.hash(newPassword));
        userRepository.save(user);
    }

    private void validateEmail(String email) {
        if (!AccountValidator.isValidEmail(email)) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
    }

    private void validatePassword(String rawPassword) {
        if (!AccountValidator.isValidPassword(rawPassword)) {
            throw new IllegalArgumentException("Passwords must be at least 8 characters long.");
        }
    }

    private UserSession toSession(VaultUser user) {
        return new UserSession(user.getId(), user.getEmail());
    }
}
