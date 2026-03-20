package com.example.desktop.model;

import java.util.Arrays;

/**
 * Database payload for one stored gallery image and its protected variant.
 */
public record StoredImageRecord(
        Long id,
        String fileName,
        String aiContext,
        String mimeType,
        long byteCount,
        int displayOrder,
        String protectedMetadata,
        byte[] imageData,
        byte[] protectedImageData) {

    public StoredImageRecord {
        fileName = fileName == null ? "" : fileName.trim();
        aiContext = aiContext == null ? "" : aiContext.trim();
        mimeType = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType;
        byteCount = Math.max(byteCount, Math.max(imageData == null ? 0 : imageData.length, protectedImageData == null ? 0 : protectedImageData.length));
        displayOrder = Math.max(displayOrder, 0);
        protectedMetadata = protectedMetadata == null ? "" : protectedMetadata;
        imageData = imageData == null ? new byte[0] : imageData.clone();
        protectedImageData = protectedImageData == null ? new byte[0] : protectedImageData.clone();
    }

    public StoredImageRecord withId(Long resolvedId) {
        return new StoredImageRecord(
                resolvedId,
                fileName,
                aiContext,
                mimeType,
                byteCount,
                displayOrder,
                protectedMetadata,
                imageData,
                protectedImageData);
    }

    @Override
    public byte[] imageData() {
        return Arrays.copyOf(imageData, imageData.length);
    }

    @Override
    public byte[] protectedImageData() {
        return Arrays.copyOf(protectedImageData, protectedImageData.length);
    }
}
