package com.example.shared.api;

/**
 * Gallery image metadata exposed by the desktop-facing API.
 */
public record ApiVaultItemImageDto(
        Long id,
        String fileName,
        String aiContext,
        String mimeType,
        long byteCount,
        int displayOrder) {
}
