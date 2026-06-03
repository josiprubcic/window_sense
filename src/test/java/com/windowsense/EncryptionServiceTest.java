package com.windowsense;

import com.windowsense.common.EncryptionException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.security.EncryptionService;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private static final String KEY = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());

    @Test
    void encryptReturnsDifferentValueAndDecryptRestoresPlaintext() {
        EncryptionService service = service(KEY);

        String encrypted = service.encrypt("device-access-token");

        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted).isNotEqualTo("device-access-token");
        assertThat(service.decrypt(encrypted)).isEqualTo("device-access-token");
    }

    @Test
    void encryptUsesRandomIvForSamePlaintext() {
        EncryptionService service = service(KEY);

        String first = service.encrypt("device-access-token");
        String second = service.encrypt("device-access-token");

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo("device-access-token");
        assertThat(service.decrypt(second)).isEqualTo("device-access-token");
    }

    @Test
    void missingKeyThrowsClearConfigurationError() {
        EncryptionService service = service("");

        assertThatThrownBy(() -> service.encrypt("device-access-token"))
                .isInstanceOf(EncryptionException.class)
                .hasMessage("APP_ENCRYPTION_KEY nije postavljen.");
    }

    @Test
    void invalidKeyThrowsClearConfigurationError() {
        EncryptionService service = service(Base64.getEncoder().encodeToString("short-key".getBytes()));

        assertThatThrownBy(() -> service.encrypt("device-access-token"))
                .isInstanceOf(EncryptionException.class)
                .hasMessage("APP_ENCRYPTION_KEY mora biti base64 encoded 32-byte key.");
    }

    @Test
    void invalidEncryptedFormatThrowsClearError() {
        EncryptionService service = service(KEY);

        assertThatThrownBy(() -> service.decrypt("invalid-format"))
                .isInstanceOf(EncryptionException.class)
                .hasMessage("Enkriptirana vrijednost nije u podrzanom formatu.");
    }

    private static EncryptionService service(String key) {
        WindowSenseProperties properties = new WindowSenseProperties();
        properties.getEncryption().setKey(key);
        return new EncryptionService(properties);
    }
}
