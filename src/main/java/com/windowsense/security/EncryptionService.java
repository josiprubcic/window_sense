package com.windowsense.security;

import com.windowsense.common.EncryptionException;
import com.windowsense.config.WindowSenseProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final String VERSION = "v1";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final WindowSenseProperties.Encryption properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(WindowSenseProperties properties) {
        this.properties = properties.getEncryption();
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new EncryptionException("Vrijednost za enkripciju ne smije biti prazna.");
        }

        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return VERSION + ":"
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException error) {
            throw new EncryptionException("Vrijednost nije moguce enkriptirati.", error);
        }
    }

    public String decrypt(String encryptedValue) {
        String[] parts = encryptedValue == null ? new String[0] : encryptedValue.split(":", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0]) || parts[1].isBlank() || parts[2].isBlank()) {
            throw new EncryptionException("Enkriptirana vrijednost nije u podrzanom formatu.");
        }

        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
            if (iv.length != IV_BYTES) {
                throw new EncryptionException("Enkriptirana vrijednost nije u podrzanom formatu.");
            }

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw new EncryptionException("Enkriptirana vrijednost nije u podrzanom formatu.", error);
        } catch (GeneralSecurityException error) {
            throw new EncryptionException("Enkriptiranu vrijednost nije moguce dekriptirati.", error);
        }
    }

    private SecretKeySpec keySpec() {
        String key = properties.getKey();
        if (key.isBlank()) {
            throw new EncryptionException("APP_ENCRYPTION_KEY nije postavljen.");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException error) {
            throw new EncryptionException("APP_ENCRYPTION_KEY mora biti base64 encoded 32-byte key.", error);
        }

        if (decoded.length != KEY_BYTES) {
            throw new EncryptionException("APP_ENCRYPTION_KEY mora biti base64 encoded 32-byte key.");
        }

        return new SecretKeySpec(decoded, KEY_ALGORITHM);
    }
}
