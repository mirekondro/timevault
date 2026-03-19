package com.example.service;

import com.example.model.VaultItem;
import com.example.repository.VaultItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class VaultItemService {

    private final VaultItemRepository repository;

    @Autowired
    public VaultItemService(VaultItemRepository repository) {
        this.repository = repository;
    }

    // Save a new vault item
    public VaultItem save(VaultItem item) {
        return repository.save(item);
    }

    // Save URL item
    public VaultItem saveUrl(String url, String title, String content, String aiContext) {
        VaultItem item = new VaultItem(title, content, "URL");
        item.setSourceUrl(url);
        item.setAiContext(aiContext);
        item.setTags(generateTags("URL", url));
        return repository.save(item);
    }

    // Save text item
    public VaultItem saveText(String title, String content, String aiContext) {
        VaultItem item = new VaultItem(title, content, "TEXT");
        item.setAiContext(aiContext);
        item.setTags(generateTags("TEXT", content));
        return repository.save(item);
    }

    // Save image item
    public VaultItem saveImage(String title, String imagePath, String aiContext) {
        VaultItem item = new VaultItem(title, imagePath, "IMAGE");
        item.setAiContext(aiContext);
        item.setTags(generateTags("IMAGE", title));
        return repository.save(item);
    }

    // Get all items
    public List<VaultItem> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    // Get recent items
    public List<VaultItem> findRecent() {
        return repository.findTop10ByOrderByCreatedAtDesc();
    }

    // Get by ID
    public Optional<VaultItem> findById(Long id) {
        return repository.findById(id);
    }

    // Get by type
    public List<VaultItem> findByType(String itemType) {
        return repository.findByItemType(itemType);
    }

    // Search
    public List<VaultItem> search(String keyword) {
        return repository.searchByKeyword(keyword);
    }

    // Delete
    public void delete(Long id) {
        repository.deleteById(id);
    }

    // Count by type
    public long countByType(String itemType) {
        return repository.countByItemType(itemType);
    }

    // Count all
    public long countAll() {
        return repository.count();
    }

    // Generate auto-tags based on content
    private String generateTags(String type, String content) {
        StringBuilder tags = new StringBuilder(type);

        // Add date tag
        tags.append(", ").append(java.time.LocalDate.now().toString());

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
}

