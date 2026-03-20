package com.example.web.api;

import com.example.shared.service.SearchService;
import com.example.shared.model.VaultItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for enhanced search functionality
 */
@RestController
@RequestMapping("/api/search")
@CrossOrigin(origins = "*")
public class SearchApiController {

    private final SearchService searchService;

    @Autowired
    public SearchApiController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Perform comprehensive search
     */
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> search(@RequestBody SearchRequest request) {
        try {
            SearchService.SearchFilter filter = new SearchService.SearchFilter();
            if (request.getFilter() != null) {
                if (request.getFilter().getItemType() != null) {
                    filter.setItemType(request.getFilter().getItemType());
                }
                filter.setFromDate(request.getFilter().getFromDate());
                filter.setToDate(request.getFilter().getToDate());
                filter.setTags(request.getFilter().getTags());
            }

            List<VaultItem> results = searchService.search(request.getQuery(), filter);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("results", results);
            response.put("count", results.size());
            response.put("query", request.getQuery());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(createErrorResponse("Search failed: " + e.getMessage()));
        }
    }

    /**
     * Get search suggestions for autocomplete
     */
    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> getSuggestions(@RequestParam String q) {
        try {
            List<SearchService.SearchSuggestion> suggestions = searchService.getSearchSuggestions(q);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("suggestions", suggestions);
            response.put("query", q);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(createErrorResponse("Suggestions failed: " + e.getMessage()));
        }
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    /**
     * Search request DTO
     */
    public static class SearchRequest {
        private String query;
        private FilterRequest filter;

        public SearchRequest() {}

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }

        public FilterRequest getFilter() { return filter; }
        public void setFilter(FilterRequest filter) { this.filter = filter; }
    }

    /**
     * Filter request DTO
     */
    public static class FilterRequest {
        private String itemType;
        private java.time.LocalDateTime fromDate;
        private java.time.LocalDateTime toDate;
        private List<String> tags;

        public FilterRequest() {}

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
