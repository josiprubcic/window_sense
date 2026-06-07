package com.windowsense.service;

import com.windowsense.config.WindowSenseProperties;
import com.windowsense.service.PhysicalDevicePairingCodeHasher;
import com.windowsense.entity.PhysicalDeviceRegistry;
import com.windowsense.repository.PhysicalDeviceRegistryRepository;
import com.windowsense.entity.PhysicalDeviceRegistryStatus;
import com.windowsense.service.PhysicalDeviceSecretHasher;
import com.windowsense.entity.WindowDevice;
import com.windowsense.repository.WindowDeviceRepository;
import com.windowsense.dto.DeviceBootstrapRequest;
import com.windowsense.security.EncryptionService;
import com.windowsense.service.DeviceBootstrapService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceBootstrapServiceTest {

    private final PhysicalDeviceRegistryRepository registryRepository = mock(PhysicalDeviceRegistryRepository.class);
    private final WindowDeviceRepository windowDeviceRepository = mock(WindowDeviceRepository.class);
    private final EncryptionService encryptionService = mock(EncryptionService.class);
    private final WindowSenseProperties properties = new WindowSenseProperties();
    private final DeviceBootstrapService service = new DeviceBootstrapService(
            registryRepository,
            windowDeviceRepository,
            encryptionService,
            properties
    );

    @Test
    void bootstrapReturnsThingsBoardTokenForVerifiedEsp() {
        properties.getThingsBoard().setHost("https://thingsboard.example");
        properties.getThingsBoard().setMqttHost("mqtt://thingsboard.example:1883");
        String sessionId = "short-lived-session";
        String deviceSecret = "esp-device-secret";
        PhysicalDeviceRegistry registry = new PhysicalDeviceRegistry(
                "WS-ESP32-0001",
                PhysicalDevicePairingCodeHasher.hash("WS-SETUP-0001"),
                "tb-device-id",
                PhysicalDeviceRegistryStatus.CLAIMED
        );
        registry.updateProvisioningMetadata(
                "esp32-chip-id",
                "1.0.0",
                "window,blinds",
                PhysicalDeviceSecretHasher.hash(deviceSecret),
                PhysicalDeviceSecretHasher.hash(sessionId),
                Instant.now().plusSeconds(60)
        );
        WindowDevice device = WindowDevice.physicalDevice("ESP32 - Kuhinja", "tb-device-id");
        device.storeEncryptedThingsBoardDeviceToken("encrypted-token");

        when(registryRepository.findBySerialNumber("WS-ESP32-0001")).thenReturn(Optional.of(registry));
        when(windowDeviceRepository.findByTbDeviceId("tb-device-id")).thenReturn(Optional.of(device));
        when(encryptionService.decrypt("encrypted-token")).thenReturn("plain-device-token");

        var response = service.bootstrap(new DeviceBootstrapRequest(
                "WS-ESP32-0001",
                deviceSecret,
                sessionId
        ));

        assertThat(response.tbDeviceId()).isEqualTo("tb-device-id");
        assertThat(response.thingsBoardAccessToken()).isEqualTo("plain-device-token");
        assertThat(response.thingsBoardMqttHost()).isEqualTo("mqtt://thingsboard.example:1883");
        assertThat(response.commandPollingUrl()).isEqualTo("/api/esp/WS-ESP32-0001/commands");
        assertThat(response.commandAckUrl()).isEqualTo("/api/esp/WS-ESP32-0001/ack");
        assertThat(registry.getProvisioningSessionHash()).isNull();
        assertThat(registry.getBootstrappedAt()).isNotNull();
    }
}
