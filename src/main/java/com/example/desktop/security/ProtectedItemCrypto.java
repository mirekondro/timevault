package com.example.desktop.security;

import com.example.desktop.model.ProtectedItemData;
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

    public LockedItemEnvelope createNewLock(ProtectedItemData data, byte[] imageBytes, String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);

        byte[] encryptionKey = deriveKey(rawPassword, salt);
        String encryptedPayload = encryptPayload(data, encryptionKey);
        byte[] encryptedImageData = encryptBytes(imageBytes, encryptionKey);
        return new LockedItemEnvelope(
                PasswordHasher.hash(rawPassword),
                Base64.getEncoder().encodeToString(salt),
                encryptedPayload,
                encryptedImageData,
                new UnlockedItemSession(data, encryptionKey, imageBytes));
    }

    public LockedItemEnvelope relockWithExistingSession(ProtectedItemData data, byte[] imageBytes, VaultItemFx item) {
        if (item == null || item.getUnlockedSession() == null) {
            throw new IllegalStateException("Cannot re-lock an item that is not unlocked in this session.");
        }

        String encryptedPayload = encryptPayload(data, item.getUnlockedSession().encryptionKey());
        byte[] rawImageBytes = imageBytes == null || imageBytes.length == 0
                ? item.getUnlockedSession().imageBytes()
                : imageBytes.clone();
        byte[] encryptedImageData = encryptBytes(rawImageBytes, item.getUnlockedSession().encryptionKey());
        return new LockedItemEnvelope(
                item.getLockPasswordHash(),
                item.getLockSalt(),
                encryptedPayload,
                encryptedImageData,
                new UnlockedItemSession(data, item.getUnlockedSession().encryptionKey(), rawImageBytes));
    }

    public UnlockedItemSession unlock(VaultItemFx item, byte[] protectedImageData, String rawPassword) {
        if (item == null || !item.isLocked()) {
            throw new IllegalArgumentException("This item is not locked.");
        }
        if (!PasswordHasher.matches(rawPassword, item.getLockPasswordHash())) {
            throw new IllegalArgumentException("Incorrect item password.");
        }

        byte[] salt = decode(item.getLockSalt());
        byte[] encryptionKey = deriveKey(rawPassword, salt);
        ProtectedItemData data = decryptPayload(item.getLockPayload(), encryptionKey);
        byte[] imageBytes = decryptBytes(protectedImageData, encryptionKey);
        return new UnlockedItemSession(data, encryptionKey, imageBytes);
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
            byte[] encryptedImageData,
            UnlockedItemSession unlockedSession) {
    }
}
