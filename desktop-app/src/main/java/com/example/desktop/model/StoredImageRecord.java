package com.example.desktop.model;

import java.util.Arrays;

/**
 * Database payload for stored image bytes and their protected variant.
 */
public record StoredImageRecord(
        String mimeType,
        long byteCount,
        byte[] imageData,
        byte[] protectedImageData) {

    public StoredImageRecord {
        mimeType = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType;
        imageData = imageData == null ? new byte[0] : imageData.clone();
        protectedImageData = protectedImageData == null ? new byte[0] : protectedImageData.clone();
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
