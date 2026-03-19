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

    public LockedItemEnvelope createNewLock(ProtectedItemData data, String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);

        byte[] encryptionKey = deriveKey(rawPassword, salt);
        String encryptedPayload = encrypt(data, encryptionKey);
        return new LockedItemEnvelope(
                PasswordHasher.hash(rawPassword),
                Base64.getEncoder().encodeToString(salt),
                encryptedPayload,
                new UnlockedItemSession(data, encryptionKey));
    }

    public LockedItemEnvelope relockWithExistingSession(ProtectedItemData data, VaultItemFx item) {
        if (item == null || item.getUnlockedSession() == null) {
            throw new IllegalStateException("Cannot re-lock an item that is not unlocked in this session.");
        }

        String encryptedPayload = encrypt(data, item.getUnlockedSession().encryptionKey());
        return new LockedItemEnvelope(
                item.getLockPasswordHash(),
                item.getLockSalt(),
                encryptedPayload,
                new UnlockedItemSession(data, item.getUnlockedSession().encryptionKey()));
    }

    public UnlockedItemSession unlock(VaultItemFx item, String rawPassword) {
        if (item == null || !item.isLocked()) {
            throw new IllegalArgumentException("This item is not locked.");
        }
        if (!PasswordHasher.matches(rawPassword, item.getLockPasswordHash())) {
            throw new IllegalArgumentException("Incorrect item password.");
        }

        byte[] salt = decode(item.getLockSalt());
        byte[] encryptionKey = deriveKey(rawPassword, salt);
        ProtectedItemData data = decrypt(item.getLockPayload(), encryptionKey);
        return new UnlockedItemSession(data, encryptionKey);
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

    private String encrypt(ProtectedItemData data, byte[] encryptionKey) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plainJson = OBJECT_MAPPER.writeValueAsBytes(data);
            byte[] encryptedBytes = cipher.doFinal(plainJson);

            return Base64.getEncoder().encodeToString(iv)
                    + "$" + Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt the protected item payload.", exception);
        }
    }

    private ProtectedItemData decrypt(String payload, byte[] encryptionKey) {
        try {
            String[] parts = payload == null ? new String[0] : payload.split("\\$", 2);
            if (parts.length != 2) {
                throw new IllegalStateException("Invalid protected item payload format.");
            }

            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encryptedBytes = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plainJson = cipher.doFinal(encryptedBytes);
            return OBJECT_MAPPER.readValue(new String(plainJson, StandardCharsets.UTF_8), ProtectedItemData.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt the protected item payload.", exception);
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
            UnlockedItemSession unlockedSession) {
    }
}
