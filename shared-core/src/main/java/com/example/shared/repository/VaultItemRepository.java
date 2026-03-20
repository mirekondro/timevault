package com.example.shared.repository;

import com.example.shared.model.VaultItem;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SHARED REPOSITORY - Used by both Web and Desktop versions
 *
 * Database access layer for VaultItem entities
 */
@Repository
public interface VaultItemRepository extends JpaRepository<VaultItem, Long> {

    // Find by item type (URL, IMAGE, TEXT)
    List<VaultItem> findByOwnerIdAndItemTypeAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, String itemType);

    // Find recent items ordered by creation date
    List<VaultItem> findTop10ByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    // Find top 3 recent items ordered by creation date
    List<VaultItem> findTop3ByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    // Find all ordered by creation date
    List<VaultItem> findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    List<VaultItem> findAllByOwnerIdOrderByCreatedAtDesc(Long userId);

    List<VaultItem> findAllByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDescCreatedAtDesc(Long userId);

    // Search by title (case-insensitive)
    List<VaultItem> findByOwnerIdAndTitleContainingIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, String keyword);

    // Search by title or content
    @Query("""
            SELECT v
            FROM VaultItem v
            WHERE v.owner.id = :userId
              AND v.deletedAt IS NULL
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
    List<VaultItem> findByOwnerIdAndTagsContainingIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, String tag);

    // Count by item type
    long countByOwnerIdAndItemTypeAndDeletedAtIsNull(Long userId, String itemType);

    long countByOwnerIdAndDeletedAtIsNull(Long userId);

    Optional<VaultItem> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long userId);

    Optional<VaultItem> findByIdAndOwnerId(Long id, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE VaultItem v
            SET v.deletedAt = CURRENT_TIMESTAMP
            WHERE v.id = :id
              AND v.owner.id = :userId
              AND v.deletedAt IS NULL
            """)
    int softDeleteByIdAndOwnerId(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE VaultItem v
            SET v.deletedAt = NULL
            WHERE v.id = :id
              AND v.owner.id = :userId
              AND v.deletedAt IS NOT NULL
            """)
    int restoreByIdAndOwnerId(@Param("id") Long id, @Param("userId") Long userId);
}

