package com.example.shared.api;

import java.time.LocalDateTime;

/**
 * Create/update payload for the desktop-facing vault API.
 */
public record ApiVaultItemMutationRequest(
        String title,
        String content,
        String aiContext,
        String itemType,
        String tags,
        String sourceUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt,
        boolean locked,
        String lockPasswordHash,
        String lockSalt,
        String lockPayload,
        ApiStoredImageDto storedImage) {
}
