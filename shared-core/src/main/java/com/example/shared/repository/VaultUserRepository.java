package com.example.shared.repository;

import com.example.shared.model.VaultUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Shared repository for user account persistence.
 */
@Repository
public interface VaultUserRepository extends JpaRepository<VaultUser, Long> {

    Optional<VaultUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
