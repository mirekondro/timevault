package com.example.shared.service;

import com.example.shared.model.VaultItem;
import com.example.shared.model.VaultUser;
import com.example.shared.repository.VaultItemRepository;
import com.example.shared.repository.VaultUserRepository;
import com.example.shared.security.PasswordHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * SHARED SERVICE - Used by both Web and Desktop versions
 *
 * Business logic for managing VaultItems with AI-powered context generation
 */
@Service
@Transactional
public class VaultItemService {

    private static final String DEFAULT_WEB_USER_EMAIL = "web-demo@timevault.local";
    private static final String DEFAULT_WEB_USER_PASSWORD = "timevault-demo-account";

    private final VaultItemRepository repository;
    private final VaultUserRepository userRepository;
    private final GeminiService geminiService;
    private final FileStorageService fileStorageService;

    @Autowired
    public VaultItemService(VaultItemRepository repository,
                           VaultUserRepository userRepository,
                           GeminiService geminiService,
                           FileStorageService fileStorageService) {
        this.repository = repository;
        this.userRepository = userRepository;
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
        return repository.save(assignDefaultOwner(item));
    }

    /**
     * Save a URL with AI-generated title and context
     */
    public VaultItem saveUrl(String url, String pageContent) {
        // Generate AI summary with proper title and description
        VaultItem aiSummary = geminiService.generateUrlSummary(url, pageContent);

        VaultItem item = new VaultItem(aiSummary.getTitle(), aiSummary.getContent(), "URL");
        item.setSourceUrl(url);
        item.setAiContext(aiSummary.getAiContext());

        // Auto-generate intelligent tags
        item.setTags(generateIntelligentTags("URL", url, pageContent, item.getAiContext()));

        return save(item);
    }

    /**
     * @deprecated Use saveUrl(String url, String pageContent) instead
     */
    @Deprecated
    public VaultItem saveUrl(String url, String title, String pageContent) {
        return saveUrl(url, pageContent);
    }

    /**
     * Save text with AI-generated context
     */
    public VaultItem saveText(String title, String content) {
        VaultItem item = new VaultItem(title, content, "TEXT");

        // Generate AI context using Gemini
        String aiContext = geminiService.generateTextContext(content);
        item.setAiContext(aiContext);

        // Auto-generate intelligent tags
        item.setTags(generateIntelligentTags("TEXT", title, content, aiContext));

        return save(item);
    }

    /**
     * Save image with AI analysis using Gemini Vision
     */
    public VaultItem saveImage(String title, byte[] imageData, String mimeType, String originalFilename) {
        try {
            // Store the image file
            String storedPath = fileStorageService.store(imageData, originalFilename);

            VaultItem item = new VaultItem(title, storedPath, "IMAGE");

            // Analyze image with Gemini Vision
            String aiContext = geminiService.analyzeImage(imageData, mimeType, originalFilename);
            item.setAiContext(aiContext);

            // Auto-generate intelligent tags
            item.setTags(generateIntelligentTags("IMAGE", originalFilename, "", aiContext));

            return save(item);
        } catch (Exception e) {
            // Fallback: save without storing file
            VaultItem item = new VaultItem(title, originalFilename, "IMAGE");
            item.setAiContext("Image saved. Analysis unavailable: " + e.getMessage());
            item.setTags(generateIntelligentTags("IMAGE", originalFilename, "", item.getAiContext()));
            return save(item);
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
            item.setTags(generateIntelligentTags("IMAGE", imagePath.getFileName().toString(), "", item.getAiContext()));
            return save(item);
        }
    }

    // ============================================
    // READ OPERATIONS
    // ============================================

    public List<VaultItem> findAll() {
        return repository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(resolveDefaultOwnerId());
    }

    public List<VaultItem> findRecent() {
        return repository.findTop10ByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(resolveDefaultOwnerId());
    }

    public List<VaultItem> findTop3Recent() {
        return repository.findTop3ByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(resolveDefaultOwnerId());
    }

    public Optional<VaultItem> findById(Long id) {
        return repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, resolveDefaultOwnerId());
    }

    public List<VaultItem> findByType(String itemType) {
        return repository.findByOwnerIdAndItemTypeAndDeletedAtIsNullOrderByCreatedAtDesc(resolveDefaultOwnerId(), itemType);
    }

    public List<VaultItem> search(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isEmpty()) {
            return findAll();
        }
        return repository.findByOwnerIdAndTitleContainingIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtDesc(
                resolveDefaultOwnerId(),
                normalizedKeyword);
    }

    public List<VaultItem> searchByAiDescription(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isEmpty()) {
            return findAll();
        }
        return findAll().stream()
                .filter(item -> containsIgnoreCase(item.getAiContext(), normalizedKeyword))
                .toList();
    }

    public List<VaultItem> searchComprehensive(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isEmpty()) {
            return findAll();
        }
        return repository.searchByUserAndKeyword(resolveDefaultOwnerId(), normalizedKeyword);
    }

    // ============================================
    // DELETE OPERATIONS
    // ============================================

    public void delete(Long id) {
        repository.softDeleteByIdAndOwnerId(id, resolveDefaultOwnerId());
    }

    public void deleteAll() {
        Long userId = resolveDefaultOwnerId();
        for (VaultItem item : repository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)) {
            repository.softDeleteByIdAndOwnerId(item.getId(), userId);
        }
    }

    // ============================================
    // COUNT OPERATIONS
    // ============================================

    public long countByType(String itemType) {
        return repository.countByOwnerIdAndItemTypeAndDeletedAtIsNull(resolveDefaultOwnerId(), itemType);
    }

    public long countAll() {
        return repository.countByOwnerIdAndDeletedAtIsNull(resolveDefaultOwnerId());
    }

    // ============================================
    // UTILITY OPERATIONS
    // ============================================

    /**
     * Regenerate AI context for an existing item
     */
    public VaultItem regenerateContext(Long id) {
        Optional<VaultItem> optItem = repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, resolveDefaultOwnerId());
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

    private String generateIntelligentTags(String type, String primaryContent, String secondaryContent, String aiContext) {
        StringBuilder tags = new StringBuilder();

        // Base type tag
        tags.append(type);

        // Date tag
        tags.append(", ").append(java.time.LocalDate.now());

        // Extract intelligent tags from AI context
        if (aiContext != null && !aiContext.isEmpty()) {
            // Look for key technical terms, tools, and concepts in AI context
            String contextLower = aiContext.toLowerCase();

            // Programming/Tech terms
            if (contextLower.contains("javascript") || contextLower.contains("js")) tags.append(", JavaScript");
            if (contextLower.contains("python")) tags.append(", Python");
            if (contextLower.contains("java") && !contextLower.contains("javascript")) tags.append(", Java");
            if (contextLower.contains("react")) tags.append(", React");
            if (contextLower.contains("vue")) tags.append(", Vue");
            if (contextLower.contains("angular")) tags.append(", Angular");
            if (contextLower.contains("node") || contextLower.contains("nodejs")) tags.append(", Node.js");
            if (contextLower.contains("docker")) tags.append(", Docker");
            if (contextLower.contains("kubernetes")) tags.append(", Kubernetes");
            if (contextLower.contains("aws") || contextLower.contains("amazon web")) tags.append(", AWS");
            if (contextLower.contains("azure")) tags.append(", Azure");
            if (contextLower.contains("google cloud") || contextLower.contains("gcp")) tags.append(", Google Cloud");

            // Content types
            if (contextLower.contains("tutorial") || contextLower.contains("guide") || contextLower.contains("how to")) tags.append(", Tutorial");
            if (contextLower.contains("documentation") || contextLower.contains("docs")) tags.append(", Documentation");
            if (contextLower.contains("api") || contextLower.contains("reference")) tags.append(", Reference");
            if (contextLower.contains("code") || contextLower.contains("snippet") || contextLower.contains("example")) tags.append(", Code");
            if (contextLower.contains("screenshot") || contextLower.contains("interface") || contextLower.contains("ui")) tags.append(", UI");
            if (contextLower.contains("diagram") || contextLower.contains("chart") || contextLower.contains("graph")) tags.append(", Diagram");
            if (contextLower.contains("article") || contextLower.contains("blog") || contextLower.contains("post")) tags.append(", Article");
            if (contextLower.contains("video") || contextLower.contains("youtube") || contextLower.contains("course")) tags.append(", Video");
            if (contextLower.contains("book") || contextLower.contains("ebook") || contextLower.contains("pdf")) tags.append(", Book");

            // Business/Work related
            if (contextLower.contains("meeting") || contextLower.contains("notes") || contextLower.contains("agenda")) tags.append(", Meeting");
            if (contextLower.contains("project") || contextLower.contains("task") || contextLower.contains("todo")) tags.append(", Project");
            if (contextLower.contains("idea") || contextLower.contains("concept") || contextLower.contains("brainstorm")) tags.append(", Ideas");
            if (contextLower.contains("recipe") || contextLower.contains("cooking") || contextLower.contains("food")) tags.append(", Recipe");
            if (contextLower.contains("travel") || contextLower.contains("trip") || contextLower.contains("vacation")) tags.append(", Travel");

            // Extract specific tools/platforms mentioned
            if (contextLower.contains("github")) tags.append(", GitHub");
            if (contextLower.contains("stackoverflow")) tags.append(", StackOverflow");
            if (contextLower.contains("medium")) tags.append(", Medium");
            if (contextLower.contains("twitter") || contextLower.contains("x.com")) tags.append(", Twitter");
            if (contextLower.contains("linkedin")) tags.append(", LinkedIn");
            if (contextLower.contains("youtube")) tags.append(", YouTube");
            if (contextLower.contains("reddit")) tags.append(", Reddit");
        }

        // Platform-specific tags for URLs
        if (type.equals("URL") && primaryContent != null) {
            if (primaryContent.contains("github.com")) tags.append(", GitHub, Code");
            else if (primaryContent.contains("medium.com")) tags.append(", Medium, Article");
            else if (primaryContent.contains("twitter.com") || primaryContent.contains("x.com")) tags.append(", Twitter, Social");
            else if (primaryContent.contains("youtube.com")) tags.append(", YouTube, Video");
            else if (primaryContent.contains("reddit.com")) tags.append(", Reddit, Discussion");
            else if (primaryContent.contains("stackoverflow.com")) tags.append(", StackOverflow, Q&A");
            else if (primaryContent.contains("linkedin.com")) tags.append(", LinkedIn, Professional");
            else if (primaryContent.contains("docs.google.com")) tags.append(", Google Docs, Document");
            else if (primaryContent.contains("notion.so")) tags.append(", Notion, Notes");
            else if (primaryContent.contains("figma.com")) tags.append(", Figma, Design");
        }

        // File type tags for images
        if (type.equals("IMAGE") && primaryContent != null) {
            String filename = primaryContent.toLowerCase();
            if (filename.endsWith(".png")) tags.append(", PNG");
            else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) tags.append(", JPEG");
            else if (filename.endsWith(".gif")) tags.append(", GIF");
            else if (filename.endsWith(".svg")) tags.append(", SVG");
            else if (filename.endsWith(".webp")) tags.append(", WebP");

            // Content-based image tags from AI analysis
            if (aiContext != null) {
                String contextLower = aiContext.toLowerCase();
                if (contextLower.contains("person") || contextLower.contains("people") || contextLower.contains("face")) tags.append(", People");
                if (contextLower.contains("animal") || contextLower.contains("dog") || contextLower.contains("cat")) tags.append(", Animals");
                if (contextLower.contains("food") || contextLower.contains("meal") || contextLower.contains("dish")) tags.append(", Food");
                if (contextLower.contains("building") || contextLower.contains("architecture") || contextLower.contains("house")) tags.append(", Architecture");
                if (contextLower.contains("nature") || contextLower.contains("landscape") || contextLower.contains("outdoor")) tags.append(", Nature");
                if (contextLower.contains("text") || contextLower.contains("document") || contextLower.contains("sign")) tags.append(", Text");
            }
        }

        return tags.toString();
    }

    // Keep the old method for compatibility but mark as deprecated
    @Deprecated
    private String generateTags(String type, String content) {
        return generateIntelligentTags(type, content, "", "");
    }

    private VaultItem assignDefaultOwner(VaultItem item) {
        VaultUser owner = resolveDefaultOwner();
        item.setOwner(owner);
        item.setUserId(owner.getId());
        return item;
    }

    private VaultUser resolveDefaultOwner() {
        return userRepository.findByEmailIgnoreCase(DEFAULT_WEB_USER_EMAIL)
                .orElseGet(() -> userRepository.save(new VaultUser(
                        DEFAULT_WEB_USER_EMAIL,
                        PasswordHasher.hash(DEFAULT_WEB_USER_PASSWORD))));
    }

    private Long resolveDefaultOwnerId() {
        return resolveDefaultOwner().getId();
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}

