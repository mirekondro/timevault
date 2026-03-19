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
