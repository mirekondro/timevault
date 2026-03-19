package com.example.shared.service;

import com.example.shared.model.VaultItem;
import com.example.shared.model.VaultUser;
import com.example.shared.repository.VaultItemRepository;
import com.example.shared.repository.VaultUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * SHARED SERVICE - Used by both Web and Desktop versions
 *
 * Business logic for managing VaultItems
 */
@Service
@Transactional
public class VaultItemService {

    private final VaultItemRepository repository;
    private final VaultUserRepository userRepository;

    @Autowired
    public VaultItemService(VaultItemRepository repository, VaultUserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    // ============================================
    // SAVE OPERATIONS
    // ============================================

    public VaultItem save(long userId, VaultItem item) {
        item.setOwner(requireUser(userId));
        item.setDeletedAt(null);
        return repository.save(item);
    }

    public VaultItem saveUrl(long userId, String url, String title, String content, String aiContext) {
        VaultItem item = new VaultItem(title, content, "URL");
        item.setOwner(requireUser(userId));
        item.setSourceUrl(url);
        item.setAiContext(aiContext);
        item.setTags(generateTags("URL", url));
        return repository.save(item);
    }

    public VaultItem saveText(long userId, String title, String content, String aiContext) {
        VaultItem item = new VaultItem(title, content, "TEXT");
        item.setOwner(requireUser(userId));
        item.setAiContext(aiContext);
        item.setTags(generateTags("TEXT", content));
        return repository.save(item);
    }

    public VaultItem saveImage(long userId, String title, String imagePath, String aiContext) {
        VaultItem item = new VaultItem(title, imagePath, "IMAGE");
        item.setOwner(requireUser(userId));
        item.setAiContext(aiContext);
        item.setTags(generateTags("IMAGE", title));
        return repository.save(item);
    }

    // ============================================
    // READ OPERATIONS
    // ============================================

    public List<VaultItem> findAll(long userId) {
        return repository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
    }

    public List<VaultItem> findRecent(long userId) {
        return repository.findTop10ByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
    }

    public Optional<VaultItem> findById(long userId, Long id) {
        return repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, userId);
    }

    public List<VaultItem> findByType(long userId, String itemType) {
        return repository.findByOwnerIdAndItemTypeAndDeletedAtIsNullOrderByCreatedAtDesc(userId, itemType);
    }

    public List<VaultItem> search(long userId, String keyword) {
        return repository.searchByUserAndKeyword(userId, keyword);
    }

    public List<VaultItem> findDeleted(long userId) {
        return repository.findAllByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDescCreatedAtDesc(userId);
    }

    // ============================================
    // DELETE OPERATIONS
    // ============================================

    public boolean delete(long userId, Long id) {
        return repository.softDeleteByIdAndOwnerId(id, userId) > 0;
    }

    public boolean restore(long userId, Long id) {
        return repository.restoreByIdAndOwnerId(id, userId) > 0;
    }

    public void deleteAll(long userId) {
        repository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(VaultItem::getId)
                .forEach(itemId -> repository.softDeleteByIdAndOwnerId(itemId, userId));
    }

    // ============================================
    // COUNT OPERATIONS
    // ============================================

    public long countByType(long userId, String itemType) {
        return repository.countByOwnerIdAndItemTypeAndDeletedAtIsNull(userId, itemType);
    }

    public long countAll(long userId) {
        return repository.countByOwnerIdAndDeletedAtIsNull(userId);
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private String generateTags(String type, String content) {
        StringBuilder tags = new StringBuilder(type);

        // Add date tag
        tags.append(", ").append(java.time.LocalDate.now());

        // Add platform tag for URLs
        if (type.equals("URL") && content != null) {
            if (content.contains("github.com")) tags.append(", GitHub");
            else if (content.contains("medium.com")) tags.append(", Medium");
            else if (content.contains("twitter.com") || content.contains("x.com")) tags.append(", Twitter");
            else if (content.contains("youtube.com")) tags.append(", YouTube");
            else if (content.contains("reddit.com")) tags.append(", Reddit");
            else if (content.contains("stackoverflow.com")) tags.append(", StackOverflow");
        }

        return tags.toString();
    }

    private VaultUser requireUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user exists with id " + userId + "."));
    }
}

