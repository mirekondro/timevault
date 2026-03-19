package com.example.shared.service;

import com.example.shared.model.VaultItem;
import com.example.shared.repository.VaultItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * SHARED SERVICE - Used by both Web and Desktop versions
 *
 * Business logic for managing VaultItems with AI-powered context generation
 */
@Service
@Transactional
public class VaultItemService {

    private final VaultItemRepository repository;
    private final GeminiService geminiService;
    private final FileStorageService fileStorageService;

    @Autowired
    public VaultItemService(VaultItemRepository repository,
                           GeminiService geminiService,
                           FileStorageService fileStorageService) {
        this.repository = repository;
        this.geminiService = geminiService;
        this.fileStorageService = fileStorageService;
    }

    // ============================================
    // SAVE OPERATIONS WITH AI CONTEXT
    // ============================================

    /**
     * Save a generic vault item
     */
    public VaultItem save(VaultItem item) {
        return repository.save(item);
    }

    /**
     * Save a URL with AI-generated context
     */
    public VaultItem saveUrl(String url, String title, String pageContent) {
        VaultItem item = new VaultItem(title, pageContent, "URL");
        item.setSourceUrl(url);
        item.setUserId(1L); // Set default user ID

        // Generate AI context using Gemini
        String aiContext = geminiService.generateUrlContext(url, pageContent);
        item.setAiContext(aiContext);

        // Auto-generate tags
        item.setTags(generateTags("URL", url));

        return repository.save(item);
    }

    /**
     * Save text with AI-generated context
     */
    public VaultItem saveText(String title, String content) {
        VaultItem item = new VaultItem(title, content, "TEXT");
        item.setUserId(1L); // Set default user ID

        // Generate AI context using Gemini
        String aiContext = geminiService.generateTextContext(content);
        item.setAiContext(aiContext);

        // Auto-generate tags
        item.setTags(generateTags("TEXT", content));

        return repository.save(item);
    }

    /**
     * Save image with AI analysis using Gemini Vision
     */
    public VaultItem saveImage(String title, byte[] imageData, String mimeType, String originalFilename) {
        try {
            // Store the image file
            String storedPath = fileStorageService.store(imageData, originalFilename);

            VaultItem item = new VaultItem(title, storedPath, "IMAGE");
            item.setUserId(1L); // Set default user ID

            // Analyze image with Gemini Vision
            String aiContext = geminiService.analyzeImage(imageData, mimeType, originalFilename);
            item.setAiContext(aiContext);

            // Auto-generate tags
            item.setTags(generateTags("IMAGE", originalFilename));

            return repository.save(item);
        } catch (Exception e) {
            // Fallback: save without storing file
            VaultItem item = new VaultItem(title, originalFilename, "IMAGE");
            item.setUserId(1L); // Set default user ID
            item.setAiContext("Image saved. Analysis unavailable: " + e.getMessage());
            item.setTags(generateTags("IMAGE", originalFilename));
            return repository.save(item);
        }
    }

    /**
     * Save image from file path with AI analysis
     */
    public VaultItem saveImage(String title, Path imagePath) {
        try {
            byte[] imageData = java.nio.file.Files.readAllBytes(imagePath);
            String mimeType = java.nio.file.Files.probeContentType(imagePath);
            return saveImage(title, imageData, mimeType, imagePath.getFileName().toString());
        } catch (Exception e) {
            VaultItem item = new VaultItem(title, imagePath.toString(), "IMAGE");
            item.setAiContext("Image reference saved. File access error: " + e.getMessage());
            item.setTags(generateTags("IMAGE", imagePath.getFileName().toString()));
            return repository.save(item);
        }
    }

    // ============================================
    // READ OPERATIONS
    // ============================================

    public List<VaultItem> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public List<VaultItem> findRecent() {
        return repository.findTop10ByOrderByCreatedAtDesc();
    }

    public Optional<VaultItem> findById(Long id) {
        return repository.findById(id);
    }

    public List<VaultItem> findByType(String itemType) {
        return repository.findByItemType(itemType);
    }

    public List<VaultItem> search(String keyword) {
        return repository.searchByKeyword(keyword);
    }

    public List<VaultItem> searchByAiDescription(String keyword) {
        return repository.searchByAiContext(keyword);
    }

    public List<VaultItem> searchComprehensive(String keyword) {
        return repository.searchComprehensive(keyword);
    }

    // ============================================
    // DELETE OPERATIONS
    // ============================================

    public void delete(Long id) {
        Optional<VaultItem> item = repository.findById(id);
        item.ifPresent(vaultItem -> {
            // Delete associated file if it's an image
            if ("IMAGE".equals(vaultItem.getItemType()) && vaultItem.getContent() != null) {
                fileStorageService.delete(vaultItem.getContent());
            }
            repository.deleteById(id);
        });
    }

    public void deleteAll() {
        repository.deleteAll();
    }

    // ============================================
    // COUNT OPERATIONS
    // ============================================

    public long countByType(String itemType) {
        return repository.countByItemType(itemType);
    }

    public long countAll() {
        return repository.count();
    }

    // ============================================
    // UTILITY OPERATIONS
    // ============================================

    /**
     * Regenerate AI context for an existing item
     */
    public VaultItem regenerateContext(Long id) {
        Optional<VaultItem> optItem = repository.findById(id);
        if (optItem.isPresent()) {
            VaultItem item = optItem.get();
            String newContext = switch (item.getItemType()) {
                case "URL" -> geminiService.generateUrlContext(item.getSourceUrl(), item.getContent());
                case "TEXT" -> geminiService.generateTextContext(item.getContent());
                case "IMAGE" -> {
                    if (fileStorageService.exists(item.getContent())) {
                        yield geminiService.analyzeImage(fileStorageService.getPath(item.getContent()));
                    } else {
                        yield item.getAiContext(); // Keep existing
                    }
                }
                default -> item.getAiContext();
            };
            item.setAiContext(newContext);
            return repository.save(item);
        }
        return null;
    }

    /**
     * Check if Gemini AI is configured
     */
    public boolean isAiConfigured() {
        return geminiService.isConfigured();
    }

    // ============================================
    // PRIVATE HELPER METHODS
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
            else if (content.contains("linkedin.com")) tags.append(", LinkedIn");
        }

        // Add file type tags for images
        if (type.equals("IMAGE") && content != null) {
            content = content.toLowerCase();
            if (content.endsWith(".png")) tags.append(", PNG");
            else if (content.endsWith(".jpg") || content.endsWith(".jpeg")) tags.append(", JPEG");
            else if (content.endsWith(".gif")) tags.append(", GIF");
            else if (content.endsWith(".webp")) tags.append(", WebP");
        }

        return tags.toString();
    }
}

