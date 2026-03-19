package com.example.shared.repository;

import com.example.shared.model.VaultItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SHARED REPOSITORY - Used by both Web and Desktop versions
 *
 * Database access layer for VaultItem entities
 */
@Repository
public interface VaultItemRepository extends JpaRepository<VaultItem, Long> {

    // Find by item type (URL, IMAGE, TEXT)
    List<VaultItem> findByItemType(String itemType);

    // Find recent items ordered by creation date
    List<VaultItem> findTop10ByOrderByCreatedAtDesc();

    // Find all ordered by creation date
    List<VaultItem> findAllByOrderByCreatedAtDesc();

    // Search by title (case-insensitive)
    List<VaultItem> findByTitleContainingIgnoreCase(String keyword);

    // Search by title or content
    @Query("SELECT v FROM VaultItem v WHERE LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(v.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<VaultItem> searchByKeyword(@Param("keyword") String keyword);

    // Find by tags containing
    List<VaultItem> findByTagsContainingIgnoreCase(String tag);

    // Count by item type
    long countByItemType(String itemType);
}

