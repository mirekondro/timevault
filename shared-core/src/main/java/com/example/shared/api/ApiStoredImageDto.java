package com.example.shared.api;

/**
 * Stored image payload used by the desktop-facing API.
 */
public record ApiStoredImageDto(
        Long id,
        String fileName,
        String aiContext,
        String mimeType,
        long byteCount,
        int displayOrder,
        String protectedMetadata,
        String imageDataBase64,
        String protectedImageDataBase64) {
}
