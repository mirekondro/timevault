package com.example.web.api;

import com.example.shared.model.VaultItem;
import com.example.shared.service.VaultItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Chrome Extension integration
 */
@RestController
@RequestMapping("/api/vault")
@CrossOrigin(origins = "*") // Allow Chrome extension to access
public class VaultApiController {

    private final VaultItemService vaultItemService;

    @Autowired
    public VaultApiController(VaultItemService vaultItemService) {
        this.vaultItemService = vaultItemService;
    }

    /**
     * Health check endpoint for extension to verify connection
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "TimeVault API");
        response.put("timestamp", System.currentTimeMillis());
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }

    /**
     * Save URL endpoint for Chrome extension
     */
    @PostMapping("/save-url")
    public ResponseEntity<Map<String, Object>> saveUrl(@RequestBody ChromeExtensionRequest request) {
        try {
            // Validate request
            if (request.getUrl() == null || request.getUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("URL is required"));
            }

            // Use the enhanced URL saving method
            VaultItem savedItem = vaultItemService.saveUrl(request.getUrl(), request.getContent());

            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Page saved successfully");
            response.put("id", savedItem.getId());
            response.put("title", savedItem.getTitle());
            response.put("aiContext", savedItem.getAiContext());
            response.put("tags", savedItem.getTags());
            response.put("timestamp", savedItem.getCreatedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to save URL: " + e.getMessage()));
        }
    }

    /**
     * Get recent items for extension
     */
    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecent(@RequestParam(defaultValue = "5") int limit) {
        try {
            List<VaultItem> items;
            if (limit <= 3) {
                items = vaultItemService.findTop3Recent();
            } else {
                items = vaultItemService.findRecent();
                if (items.size() > limit) {
                    items = items.subList(0, limit);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("items", items);
            response.put("count", items.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to fetch recent items: " + e.getMessage()));
        }
    }

    /**
     * Search items for extension
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Search query is required"));
            }

            List<VaultItem> items = vaultItemService.searchComprehensive(query.trim());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("items", items);
            response.put("count", items.size());
            response.put("query", query);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Search failed: " + e.getMessage()));
        }
    }

    /**
     * Get vault statistics for extension
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalItems", vaultItemService.countAll());
            response.put("urlCount", vaultItemService.countByType("URL"));
            response.put("imageCount", vaultItemService.countByType("IMAGE"));
            response.put("textCount", vaultItemService.countByType("TEXT"));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to get stats: " + e.getMessage()));
        }
    }

    /**
     * Delete item endpoint for extension
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteItem(@PathVariable Long id) {
        try {
            vaultItemService.delete(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Item deleted successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to delete item: " + e.getMessage()));
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
     * Request DTO for Chrome extension
     */
    public static class ChromeExtensionRequest {
        private String url;
        private String title;
        private String content;
        private String description;
        private String source;

        // Constructors
        public ChromeExtensionRequest() {}

        // Getters and Setters
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }
}
