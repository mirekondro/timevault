package com.example.desktop.security;

import com.example.desktop.model.GalleryImageFx;
import com.example.desktop.model.ProtectedItemData;
import com.example.desktop.model.StoredImageRecord;
import com.example.desktop.model.UnlockedItemSession;
import com.example.desktop.model.VaultItemFx;
import com.example.shared.security.PasswordHasher;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Encrypts and decrypts item-level protected payloads for the desktop vault.
 */
public final class ProtectedItemCrypto {

    private static final String KEY_DERIVATION = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_ITERATIONS = 120_000;
    private static final int KEY_BYTES = 32;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public LockedItemEnvelope createNewLock(ProtectedItemData data, List<StoredImageRecord> images, String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);

        byte[] encryptionKey = deriveKey(rawPassword, salt);
        String encryptedPayload = encryptPayload(data, encryptionKey);
        List<StoredImageRecord> encryptedImages = encryptImages(images, encryptionKey, List.of());
        return new LockedItemEnvelope(
                PasswordHasher.hash(rawPassword),
                Base64.getEncoder().encodeToString(salt),
                encryptedPayload,
                encryptedImages,
                new UnlockedItemSession(data, encryptionKey, toUnlockedGalleryImages(images, List.of())));
    }

    public LockedItemEnvelope relockWithExistingSession(ProtectedItemData data, List<StoredImageRecord> images, VaultItemFx item) {
        if (item == null || item.getUnlockedSession() == null) {
            throw new IllegalStateException("Cannot re-lock an item that is not unlocked in this session.");
        }

        String encryptedPayload = encryptPayload(data, item.getUnlockedSession().encryptionKey());
        List<GalleryImageFx> sessionImages = item.getUnlockedSession().galleryImages();
        List<StoredImageRecord> encryptedImages = encryptImages(images, item.getUnlockedSession().encryptionKey(), sessionImages);
        return new LockedItemEnvelope(
                item.getLockPasswordHash(),
                item.getLockSalt(),
                encryptedPayload,
                encryptedImages,
                new UnlockedItemSession(data, item.getUnlockedSession().encryptionKey(), toUnlockedGalleryImages(images, sessionImages)));
    }

    public UnlockedItemSession unlock(VaultItemFx item, List<StoredImageRecord> protectedImages, String rawPassword) {
        if (item == null || !item.isLocked()) {
            throw new IllegalArgumentException("This item is not locked.");
        }
        if (!PasswordHasher.matches(rawPassword, item.getLockPasswordHash())) {
            throw new IllegalArgumentException("Incorrect item password.");
        }

        byte[] salt = decode(item.getLockSalt());
        byte[] encryptionKey = deriveKey(rawPassword, salt);
        ProtectedItemData data = decryptPayload(item.getLockPayload(), encryptionKey);
        List<GalleryImageFx> unlockedImages = decryptImages(protectedImages, encryptionKey, data);
        return new UnlockedItemSession(data, encryptionKey, unlockedImages);
    }

    private byte[] deriveKey(String rawPassword, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, KEY_ITERATIONS, KEY_BYTES * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION);
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not derive the protected-item encryption key.", exception);
        }
    }

    private String encryptPayload(ProtectedItemData data, byte[] encryptionKey) {
        try {
            byte[] plainJson = OBJECT_MAPPER.writeValueAsBytes(data);
            return Base64.getEncoder().encodeToString(encryptBytes(plainJson, encryptionKey));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt the protected item payload.", exception);
        }
    }

    private ProtectedItemData decryptPayload(String payload, byte[] encryptionKey) {
        try {
            if (payload == null || payload.isBlank()) {
                return new ProtectedItemData("", "", "", "", "");
            }
            byte[] plainJson = decryptBytes(Base64.getDecoder().decode(payload), encryptionKey);
            return OBJECT_MAPPER.readValue(new String(plainJson, StandardCharsets.UTF_8), ProtectedItemData.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt the protected item payload.", exception);
        }
    }

    private List<StoredImageRecord> encryptImages(List<StoredImageRecord> images,
                                                  byte[] encryptionKey,
                                                  List<GalleryImageFx> sessionImages) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        return images.stream()
                .filter(image -> image != null && image.byteCount() > 0L)
                .sorted(Comparator.comparingInt(StoredImageRecord::displayOrder)
                        .thenComparing(image -> image.id() == null ? Long.MAX_VALUE : image.id()))
                .map(image -> {
                    byte[] plainBytes = image.imageData().length > 0
                            ? image.imageData()
                            : findMatchingSessionImage(image, sessionImages)
                                    .map(GalleryImageFx::getCachedImageBytes)
                                    .orElseGet(() -> new byte[0]);
                    String resolvedFileName = !image.fileName().isBlank()
                            ? image.fileName()
                            : findMatchingSessionImage(image, sessionImages)
                                    .map(GalleryImageFx::getFileName)
                                    .orElse("");
                    String resolvedAiContext = !image.aiContext().isBlank()
                            ? image.aiContext()
                            : findMatchingSessionImage(image, sessionImages)
                                    .map(GalleryImageFx::getAiContext)
                                    .orElse("");

                    return new StoredImageRecord(
                            image.id(),
                            "",
                            "",
                            image.mimeType(),
                            image.byteCount(),
                            image.displayOrder(),
                            encryptImageMetadata(new ProtectedImageMetadata(resolvedFileName, resolvedAiContext), encryptionKey),
                            new byte[0],
                            encryptBytes(plainBytes, encryptionKey));
                })
                .toList();
    }

    private List<GalleryImageFx> decryptImages(List<StoredImageRecord> protectedImages,
                                               byte[] encryptionKey,
                                               ProtectedItemData data) {
        if (protectedImages == null || protectedImages.isEmpty()) {
            return List.of();
        }

        boolean legacySingleImage = protectedImages.size() == 1
                && protectedImages.getFirst().protectedMetadata().isBlank();

        return protectedImages.stream()
                .filter(image -> image != null && image.byteCount() > 0L)
                .sorted(Comparator.comparingInt(StoredImageRecord::displayOrder)
                        .thenComparing(image -> image.id() == null ? Long.MAX_VALUE : image.id()))
                .map(image -> {
                    ProtectedImageMetadata metadata = legacySingleImage
                            ? new ProtectedImageMetadata(data.content(), data.aiContext())
                            : decryptImageMetadata(image.protectedMetadata(), encryptionKey);
                    GalleryImageFx unlockedImage = new GalleryImageFx();
                    unlockedImage.setId(image.id() == null ? 0L : image.id());
                    unlockedImage.setFileName(metadata.fileName());
                    unlockedImage.setAiContext(metadata.aiContext());
                    unlockedImage.setMimeType(image.mimeType());
                    unlockedImage.setByteCount(image.byteCount());
                    unlockedImage.setDisplayOrder(image.displayOrder());
                    unlockedImage.setCachedImageBytes(decryptBytes(image.protectedImageData(), encryptionKey));
                    return unlockedImage;
                })
                .toList();
    }

    private String encryptImageMetadata(ProtectedImageMetadata metadata, byte[] encryptionKey) {
        try {
            byte[] plainJson = OBJECT_MAPPER.writeValueAsBytes(metadata);
            return Base64.getEncoder().encodeToString(encryptBytes(plainJson, encryptionKey));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt gallery image metadata.", exception);
        }
    }

    private ProtectedImageMetadata decryptImageMetadata(String payload, byte[] encryptionKey) {
        try {
            if (payload == null || payload.isBlank()) {
                return new ProtectedImageMetadata("", "");
            }
            byte[] plainJson = decryptBytes(Base64.getDecoder().decode(payload), encryptionKey);
            return OBJECT_MAPPER.readValue(new String(plainJson, StandardCharsets.UTF_8), ProtectedImageMetadata.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt gallery image metadata.", exception);
        }
    }

    private List<GalleryImageFx> toUnlockedGalleryImages(List<StoredImageRecord> images, List<GalleryImageFx> sessionImages) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
                .filter(image -> image != null && image.byteCount() > 0L)
                .sorted(Comparator.comparingInt(StoredImageRecord::displayOrder)
                        .thenComparing(image -> image.id() == null ? Long.MAX_VALUE : image.id()))
                .map(image -> {
                    GalleryImageFx unlockedImage = new GalleryImageFx();
                    unlockedImage.setId(image.id() == null ? 0L : image.id());
                    unlockedImage.setFileName(!image.fileName().isBlank()
                            ? image.fileName()
                            : findMatchingSessionImage(image, sessionImages)
                                    .map(GalleryImageFx::getFileName)
                                    .orElse(""));
                    unlockedImage.setAiContext(!image.aiContext().isBlank()
                            ? image.aiContext()
                            : findMatchingSessionImage(image, sessionImages)
                                    .map(GalleryImageFx::getAiContext)
                                    .orElse(""));
                    unlockedImage.setMimeType(image.mimeType());
                    unlockedImage.setByteCount(image.byteCount());
                    unlockedImage.setDisplayOrder(image.displayOrder());
                    unlockedImage.setCachedImageBytes(image.imageData().length > 0
                            ? image.imageData()
                            : findMatchingSessionImage(image, sessionImages)
                                    .map(GalleryImageFx::getCachedImageBytes)
                                    .orElseGet(() -> new byte[0]));
                    return unlockedImage;
                })
                .toList();
    }

    private java.util.Optional<GalleryImageFx> findMatchingSessionImage(StoredImageRecord image, List<GalleryImageFx> sessionImages) {
        if (image == null || sessionImages == null || sessionImages.isEmpty()) {
            return java.util.Optional.empty();
        }
        return sessionImages.stream()
                .filter(sessionImage -> {
                    if (image.id() != null && image.id() > 0L && sessionImage.getId() > 0L) {
                        return image.id() == sessionImage.getId();
                    }
                    return image.displayOrder() == sessionImage.getDisplayOrder();
                })
                .findFirst();
    }

    private byte[] encryptBytes(byte[] plainBytes, byte[] encryptionKey) {
        if (plainBytes == null || plainBytes.length == 0) {
            return new byte[0];
        }

        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encryptedBytes = cipher.doFinal(plainBytes);
            byte[] payload = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, payload, iv.length, encryptedBytes.length);
            return payload;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt the protected image payload.", exception);
        }
    }

    private byte[] decryptBytes(byte[] payload, byte[] encryptionKey) {
        if (payload == null || payload.length == 0) {
            return new byte[0];
        }
        if (payload.length <= IV_BYTES) {
            throw new IllegalStateException("Invalid protected image payload format.");
        }

        try {
            byte[] iv = new byte[IV_BYTES];
            byte[] encryptedBytes = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encryptedBytes, 0, encryptedBytes.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(encryptedBytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt the protected image payload.", exception);
        }
    }

    private byte[] decode(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid protected-item salt encoding.", exception);
        }
    }

    public record LockedItemEnvelope(
            String passwordHash,
            String lockSalt,
            String encryptedPayload,
            List<StoredImageRecord> encryptedImages,
            UnlockedItemSession unlockedSession) {
    }

    private record ProtectedImageMetadata(String fileName, String aiContext) {
    }
}
