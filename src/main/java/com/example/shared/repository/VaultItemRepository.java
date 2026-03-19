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
    List<VaultItem> findByOwnerIdAndItemTypeOrderByCreatedAtDesc(Long userId, String itemType);

    // Find recent items ordered by creation date
    List<VaultItem> findTop10ByOwnerIdOrderByCreatedAtDesc(Long userId);

    // Find all ordered by creation date
    List<VaultItem> findAllByOwnerIdOrderByCreatedAtDesc(Long userId);

    // Search by title (case-insensitive)
    List<VaultItem> findByOwnerIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(Long userId, String keyword);

    // Search by title or content
    @Query("""
            SELECT v
            FROM VaultItem v
            WHERE v.owner.id = :userId
              AND (
                  LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(v.content, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(v.aiContext, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(v.tags, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(v.sourceUrl, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY v.createdAt DESC
            """)
    List<VaultItem> searchByUserAndKeyword(@Param("userId") Long userId, @Param("keyword") String keyword);

    // Find by tags containing
    List<VaultItem> findByOwnerIdAndTagsContainingIgnoreCaseOrderByCreatedAtDesc(Long userId, String tag);

    // Count by item type
    long countByOwnerIdAndItemType(Long userId, String itemType);

    long countByOwnerId(Long userId);

    java.util.Optional<VaultItem> findByIdAndOwnerId(Long id, Long userId);

    long deleteByIdAndOwnerId(Long id, Long userId);
}

