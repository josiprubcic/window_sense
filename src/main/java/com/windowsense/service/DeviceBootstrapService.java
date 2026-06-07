package com.windowsense.service;

import com.windowsense.exception.ConflictException;
import com.windowsense.exception.ResourceNotFoundException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.entity.PhysicalDeviceRegistry;
import com.windowsense.repository.PhysicalDeviceRegistryRepository;
import com.windowsense.service.PhysicalDeviceSecretHasher;
import com.windowsense.entity.WindowDevice;
import com.windowsense.repository.WindowDeviceRepository;
import com.windowsense.dto.DeviceBootstrapRequest;
import com.windowsense.dto.DeviceBootstrapResponse;
import com.windowsense.security.EncryptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceBootstrapService {

    private final PhysicalDeviceRegistryRepository physicalDeviceRegistryRepository;
    private final WindowDeviceRepository windowDeviceRepository;
    private final EncryptionService encryptionService;
    private final WindowSenseProperties properties;

    public DeviceBootstrapService(
            PhysicalDeviceRegistryRepository physicalDeviceRegistryRepository,
            WindowDeviceRepository windowDeviceRepository,
            EncryptionService encryptionService,
            WindowSenseProperties properties
    ) {
        this.physicalDeviceRegistryRepository = physicalDeviceRegistryRepository;
        this.windowDeviceRepository = windowDeviceRepository;
        this.encryptionService = encryptionService;
        this.properties = properties;
    }

    @Transactional
    public DeviceBootstrapResponse bootstrap(DeviceBootstrapRequest request) {
        String serialNumber = required(request.serialNumber(), "Serijski broj uredjaja je obavezan.");
        PhysicalDeviceRegistry registry = physicalDeviceRegistryRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Uredjaj nije registriran za bootstrap."));

        String sessionHash = PhysicalDeviceSecretHasher.hash(request.provisioningSessionId());
        if (!registry.matchesProvisioningSession(sessionHash)) {
            throw new ConflictException("Provisioning session nije valjan ili je istekao.");
        }

        String secretHash = PhysicalDeviceSecretHasher.hash(request.deviceSecret());
        if (!registry.matchesDeviceSecret(secretHash)) {
            throw new ConflictException("Tajni kljuc uredjaja nije valjan.");
        }

        WindowDevice device = windowDeviceRepository.findByTbDeviceId(registry.getTbDeviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Lokalni uredjaj nije pronadjen."));
        if (device.getTbDeviceTokenEncrypted() == null || device.getTbDeviceTokenEncrypted().isBlank()) {
            throw new ConflictException("ThingsBoard access token nije spremljen za uredjaj.");
        }

        String accessToken = encryptionService.decrypt(device.getTbDeviceTokenEncrypted());
        registry.markBootstrapped();
        return new DeviceBootstrapResponse(
                registry.getSerialNumber(),
                registry.getTbDeviceId(),
                properties.getThingsBoard().getHost(),
                thingsBoardMqttHost(),
                accessToken,
                "/api/esp/" + registry.getSerialNumber() + "/commands",
                "/api/esp/" + registry.getSerialNumber() + "/ack"
        );
    }

    private String thingsBoardMqttHost() {
        String mqttHost = properties.getThingsBoard().getMqttHost();
        return mqttHost == null || mqttHost.isBlank()
                ? properties.getThingsBoard().getHost()
                : mqttHost;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
