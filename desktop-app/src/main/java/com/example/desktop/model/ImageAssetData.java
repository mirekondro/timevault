package com.example.desktop.model;

import java.util.Arrays;

/**
 * Raw image asset selected or loaded for an image vault item.
 */
public record ImageAssetData(
        String fileName,
        String mimeType,
        byte[] bytes) {

    public ImageAssetData {
        fileName = fileName == null ? "" : fileName;
        mimeType = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType;
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    public long size() {
        return bytes.length;
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
