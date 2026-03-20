package com.example.shared.api;

/**
 * Stored image payload used by the desktop-facing API.
 */
public record ApiStoredImageDto(
        String mimeType,
        long byteCount,
        String imageDataBase64,
        String protectedImageDataBase64) {
}
