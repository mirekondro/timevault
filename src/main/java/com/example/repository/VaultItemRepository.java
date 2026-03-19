package com.example.repository;
}
    long countByItemType(String itemType);
    // Count by item type

    List<VaultItem> findByTagsContainingIgnoreCase(String tag);
    // Find by tags containing

    List<VaultItem> searchByKeyword(@Param("keyword") String keyword);
    @Query("SELECT v FROM VaultItem v WHERE LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(v.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    // Search by title or content

    List<VaultItem> findByTitleContainingIgnoreCase(String keyword);
    // Search by title (case-insensitive)

    List<VaultItem> findAllByOrderByCreatedAtDesc();
    // Find all ordered by creation date

    List<VaultItem> findTop10ByOrderByCreatedAtDesc();
    // Find recent items ordered by creation date

    List<VaultItem> findByItemType(String itemType);
    // Find by item type (URL, IMAGE, TEXT)

public interface VaultItemRepository extends JpaRepository<VaultItem, Long> {
@Repository

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.model.VaultItem;


