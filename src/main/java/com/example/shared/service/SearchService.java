package com.example.shared.service;

import com.example.shared.model.VaultItem;
import com.example.shared.repository.VaultItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced search service with advanced filtering and ranking capabilities
 */
@Service
public class SearchService {

    private final VaultItemRepository vaultItemRepository;

    @Autowired
    public SearchService(VaultItemRepository vaultItemRepository) {
        this.vaultItemRepository = vaultItemRepository;
    }

    /**
     * Perform comprehensive search across all vault items
     */
    public List<VaultItem> search(String query, SearchFilter filter) {
        if (query == null || query.trim().isEmpty()) {
            return getAllItemsByFilter(filter);
        }

        String searchTerm = query.toLowerCase().trim();
        List<VaultItem> allItems = vaultItemRepository.findAll();

        return allItems.stream()
                .filter(item -> matchesFilter(item, filter))
                .filter(item -> matchesSearchTerm(item, searchTerm))
                .sorted((a, b) -> calculateRelevanceScore(b, searchTerm) - calculateRelevanceScore(a, searchTerm))
                .limit(50) // Limit results for performance
                .collect(Collectors.toList());
    }

    /**
     * Quick search for autocomplete suggestions
     */
    public List<SearchSuggestion> getSearchSuggestions(String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        String searchTerm = query.toLowerCase().trim();
        List<VaultItem> allItems = vaultItemRepository.findAll();

        return allItems.stream()
                .filter(item -> matchesSearchTerm(item, searchTerm))
                .limit(5)
                .map(item -> new SearchSuggestion(
                        item.getTitle(),
                        truncateContent(item.getAiContext(), 80),
                        item.getItemType(),
                        item.getId()
                ))
                .collect(Collectors.toList());
    }

    private List<VaultItem> getAllItemsByFilter(SearchFilter filter) {
        List<VaultItem> allItems = vaultItemRepository.findAll();
        return allItems.stream()
                .filter(item -> matchesFilter(item, filter))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(50)
                .collect(Collectors.toList());
    }

    private boolean matchesFilter(VaultItem item, SearchFilter filter) {
        if (filter == null) return true;

        // Filter by type
        if (filter.getItemType() != null && !filter.getItemType().equals(item.getItemType())) {
            return false;
        }

        // Filter by date range
        if (filter.getFromDate() != null && item.getCreatedAt().isBefore(filter.getFromDate())) {
            return false;
        }
        if (filter.getToDate() != null && item.getCreatedAt().isAfter(filter.getToDate())) {
            return false;
        }

        // Filter by tags
        if (filter.getTags() != null && !filter.getTags().isEmpty()) {
            String itemTags = item.getTags() != null ? item.getTags().toLowerCase() : "";
            return filter.getTags().stream()
                    .anyMatch(tag -> itemTags.contains(tag.toLowerCase()));
        }

        return true;
    }

    private boolean matchesSearchTerm(VaultItem item, String searchTerm) {
        // Search in title
        if (item.getTitle() != null && item.getTitle().toLowerCase().contains(searchTerm)) {
            return true;
        }

        // Search in content
        if (item.getContent() != null && item.getContent().toLowerCase().contains(searchTerm)) {
            return true;
        }

        // Search in AI context
        if (item.getAiContext() != null && item.getAiContext().toLowerCase().contains(searchTerm)) {
            return true;
        }

        // Search in tags
        if (item.getTags() != null && item.getTags().toLowerCase().contains(searchTerm)) {
            return true;
        }

        // Search in source URL
        if (item.getSourceUrl() != null && item.getSourceUrl().toLowerCase().contains(searchTerm)) {
            return true;
        }

        return false;
    }

    private int calculateRelevanceScore(VaultItem item, String searchTerm) {
        int score = 0;

        // Title match (highest priority)
        if (item.getTitle() != null) {
            String title = item.getTitle().toLowerCase();
            if (title.startsWith(searchTerm)) score += 100;
            else if (title.contains(searchTerm)) score += 50;
        }

        // AI context match (high priority)
        if (item.getAiContext() != null) {
            String context = item.getAiContext().toLowerCase();
            if (context.contains(searchTerm)) score += 30;
        }

        // Content match (medium priority)
        if (item.getContent() != null) {
            String content = item.getContent().toLowerCase();
            if (content.contains(searchTerm)) score += 20;
        }

        // Tags match (medium priority)
        if (item.getTags() != null) {
            String tags = item.getTags().toLowerCase();
            if (tags.contains(searchTerm)) score += 25;
        }

        // URL match (lower priority)
        if (item.getSourceUrl() != null) {
            String url = item.getSourceUrl().toLowerCase();
            if (url.contains(searchTerm)) score += 10;
        }

        // Boost recent items
        long daysSinceCreated = java.time.temporal.ChronoUnit.DAYS.between(
                item.getCreatedAt().toLocalDate(),
                java.time.LocalDate.now()
        );
        if (daysSinceCreated < 7) score += 15;
        else if (daysSinceCreated < 30) score += 5;

        return score;
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }

    /**
     * Search suggestion data class
     */
    public static class SearchSuggestion {
        private final String title;
        private final String snippet;
        private final String type;
        private final Long id;

        public SearchSuggestion(String title, String snippet, String type, Long id) {
            this.title = title;
            this.snippet = snippet;
            this.type = type;
            this.id = id;
        }

        public String getTitle() { return title; }
        public String getSnippet() { return snippet; }
        public String getType() { return type; }
        public Long getId() { return id; }
    }

    /**
     * Search filter data class
     */
    public static class SearchFilter {
        private String itemType;
        private java.time.LocalDateTime fromDate;
        private java.time.LocalDateTime toDate;
        private List<String> tags;

        public SearchFilter() {}

        public String getItemType() { return itemType; }
        public void setItemType(String itemType) { this.itemType = itemType; }

        public java.time.LocalDateTime getFromDate() { return fromDate; }
        public void setFromDate(java.time.LocalDateTime fromDate) { this.fromDate = fromDate; }

        public java.time.LocalDateTime getToDate() { return toDate; }
        public void setToDate(java.time.LocalDateTime toDate) { this.toDate = toDate; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }
}
