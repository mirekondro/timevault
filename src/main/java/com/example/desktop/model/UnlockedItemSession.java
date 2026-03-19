package com.example.desktop.model;

import java.util.Arrays;

/**
 * In-memory unlocked state for a protected item during the current desktop session.
 */
public final class UnlockedItemSession {

    private final ProtectedItemData data;
    private final byte[] encryptionKey;
    private final byte[] imageBytes;

    public UnlockedItemSession(ProtectedItemData data, byte[] encryptionKey, byte[] imageBytes) {
        this.data = data;
        this.encryptionKey = encryptionKey == null ? new byte[0] : encryptionKey.clone();
        this.imageBytes = imageBytes == null ? new byte[0] : imageBytes.clone();
    }

    public ProtectedItemData data() {
        return data;
    }

    public byte[] encryptionKey() {
        return encryptionKey.clone();
    }

    public byte[] imageBytes() {
        return imageBytes.clone();
    }

    public UnlockedItemSession copy() {
        return new UnlockedItemSession(data, encryptionKey, imageBytes);
    }

    @Override
    public String toString() {
        return "UnlockedItemSession{"
                + "data=" + data
                + ", encryptionKeyBytes=" + encryptionKey.length
                + ", imageBytes=" + imageBytes.length
                + '}';
    }

    @Override
    public int hashCode() {
        int result = data != null ? data.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(encryptionKey);
        result = 31 * result + Arrays.hashCode(imageBytes);
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
                && Arrays.equals(imageBytes, that.imageBytes);
    }
}
