package com.example.desktop.model;

import java.util.Arrays;
import java.util.List;

/**
 * In-memory unlocked state for a protected item during the current desktop session.
 */
public final class UnlockedItemSession {

    private final ProtectedItemData data;
    private final byte[] encryptionKey;
    private final List<GalleryImageFx> galleryImages;

    public UnlockedItemSession(ProtectedItemData data, byte[] encryptionKey, List<GalleryImageFx> galleryImages) {
        this.data = data;
        this.encryptionKey = encryptionKey == null ? new byte[0] : encryptionKey.clone();
        this.galleryImages = galleryImages == null ? List.of() : galleryImages.stream()
                .map(GalleryImageFx::copy)
                .toList();
    }

    public ProtectedItemData data() {
        return data;
    }

    public byte[] encryptionKey() {
        return encryptionKey.clone();
    }

    public List<GalleryImageFx> galleryImages() {
        return galleryImages.stream()
                .map(GalleryImageFx::copy)
                .toList();
    }

    public byte[] imageBytes() {
        return galleryImages.isEmpty() ? new byte[0] : galleryImages.getFirst().getCachedImageBytes();
    }

    public UnlockedItemSession copy() {
        return new UnlockedItemSession(data, encryptionKey, galleryImages);
    }

    @Override
    public String toString() {
        return "UnlockedItemSession{"
                + "data=" + data
                + ", encryptionKeyBytes=" + encryptionKey.length
                + ", galleryImages=" + galleryImages.size()
                + '}';
    }

    @Override
    public int hashCode() {
        int result = data != null ? data.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(encryptionKey);
        result = 31 * result + galleryImages.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UnlockedItemSession that)) {
            return false;
        }
        return java.util.Objects.equals(data, that.data)
                && Arrays.equals(encryptionKey, that.encryptionKey)
                && java.util.Objects.equals(galleryImages, that.galleryImages);
    }
}
