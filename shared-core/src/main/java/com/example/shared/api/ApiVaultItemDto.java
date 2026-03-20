package com.example.shared.api;

import java.time.LocalDateTime;

/**
 * Desktop-facing vault item response that mirrors the current local DAO shape.
 */
public record ApiVaultItemDto(
        Long id,
        Long ownerId,
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
        String imageMimeType,
        long imageByteCount) {
}
