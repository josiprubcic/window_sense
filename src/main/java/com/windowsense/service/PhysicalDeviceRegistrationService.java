package com.windowsense.service;

import com.windowsense.exception.ConflictException;
import com.windowsense.dto.RegisterPhysicalEspTokenRequest;
import com.windowsense.dto.RegisterPhysicalEspTokenOnlyRequest;
import com.windowsense.dto.RegisterPhysicalEspTokenOnlyResponse;
import com.windowsense.dto.RegisterPhysicalEspTokenResponse;
import com.windowsense.entity.DeviceCapabilities;
import com.windowsense.entity.DeviceCapability;
import com.windowsense.entity.PhysicalDeviceRegistry;
import com.windowsense.entity.PhysicalDeviceRegistryStatus;
import com.windowsense.integration.thingsboard.PhysicalEspTokenRegistrationRequest;
import com.windowsense.integration.thingsboard.RegisteredPhysicalEspDevice;
import com.windowsense.integration.thingsboard.ThingsBoardProvisioningService;
import com.windowsense.repository.PhysicalDeviceRegistryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PhysicalDeviceRegistrationService {

    private static final String PAIRING_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final PhysicalDeviceRegistryRepository physicalDeviceRegistryRepository;
    private final ThingsBoardProvisioningService thingsBoardProvisioningService;

    public PhysicalDeviceRegistrationService(
            PhysicalDeviceRegistryRepository physicalDeviceRegistryRepository,
            ThingsBoardProvisioningService thingsBoardProvisioningService
    ) {
        this.physicalDeviceRegistryRepository = physicalDeviceRegistryRepository;
        this.thingsBoardProvisioningService = thingsBoardProvisioningService;
    }

    @Transactional
    public RegisterPhysicalEspTokenOnlyResponse registerWithHardcodedTokenOnly(RegisterPhysicalEspTokenOnlyRequest request) {
        String accessToken = required(request.thingsBoardAccessToken(), "ThingsBoard access token je obavezan.");
        String suffix = randomDeviceSuffix();
        String deviceName = "WindowSense ESP32 " + suffix;
        String serialNumber = "WS-ESP32-" + suffix;
        String pairingCode = generatePairingCode();

        RegisterPhysicalEspTokenResponse response = registerWithHardcodedToken(new RegisterPhysicalEspTokenRequest(
                deviceName,
                serialNumber,
                null,
                null,
                List.of("window", "blinds", "rain", "lux", "temperature", "wind"),
                pairingCode,
                accessToken
        ));

        return new RegisterPhysicalEspTokenOnlyResponse(
                response.serialNumber(),
                deviceName,
                pairingCode,
                response.tbDeviceId(),
                response.status()
        );
    }

    @Transactional
    public RegisterPhysicalEspTokenResponse registerWithHardcodedToken(RegisterPhysicalEspTokenRequest request) {
        String deviceName = required(request.deviceName(), "Naziv uredjaja je obavezan.");
        String serialNumber = required(request.serialNumber(), "Serijski broj ESP uredjaja je obavezan.");
        String pairingCode = required(request.pairingCode(), "Kod za povezivanje je obavezan.");
        String accessToken = required(request.thingsBoardAccessToken(), "ThingsBoard access token je obavezan.");
        String accessTokenHash = PhysicalDeviceSecretHasher.hash(accessToken);
        String hardwareId = request.hardwareId() == null ? null : request.hardwareId().trim();

        if (physicalDeviceRegistryRepository.existsByThingsBoardAccessTokenHash(accessTokenHash)) {
            throw new ConflictException("ESP uredjaj s tim ThingsBoard access tokenom vec postoji.");
        }
        if (physicalDeviceRegistryRepository.existsBySerialNumber(serialNumber)) {
            throw new ConflictException("ESP uredjaj s tim serijskim brojem vec postoji.");
        }
        if (hardwareId != null && !hardwareId.isBlank() && physicalDeviceRegistryRepository.existsByHardwareId(hardwareId)) {
            throw new ConflictException("ESP uredjaj s tim hardware ID-em vec postoji.");
        }

        List<DeviceCapability> capabilities = DeviceCapabilities.fromLabels(request.capabilities()).stream().toList();
        List<String> capabilityLabels = capabilities.stream().map(DeviceCapability::name).toList();
        RegisteredPhysicalEspDevice registered = thingsBoardProvisioningService.registerPhysicalEspDeviceWithToken(
                new PhysicalEspTokenRegistrationRequest(
                        deviceName,
                        serialNumber,
                        hardwareId,
                        request.firmwareVersion(),
                        capabilityLabels,
                        accessToken
                )
        );

        PhysicalDeviceRegistry registry = new PhysicalDeviceRegistry(
                serialNumber,
                PhysicalDevicePairingCodeHasher.hash(pairingCode),
                registered.tbDeviceId(),
                PhysicalDeviceRegistryStatus.CLAIMABLE
        );
        registry.setThingsBoardAccessTokenHash(accessTokenHash);
        registry.updateRegistrationMetadata(
                hardwareId,
                request.firmwareVersion(),
                String.join(",", capabilityLabels),
                capabilities
        );
        physicalDeviceRegistryRepository.save(registry);
        return new RegisterPhysicalEspTokenResponse(
                registry.getSerialNumber(),
                registry.getHardwareId(),
                registry.getTbDeviceId(),
                registry.getStatus().name()
        );
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String generatePairingCode() {
        StringBuilder code = new StringBuilder("WS-");
        for (int index = 0; index < 8; index++) {
            code.append(randomAlphabetCharacter());
        }
        return code.toString();
    }

    private String randomDeviceSuffix() {
        StringBuilder suffix = new StringBuilder();
        for (int index = 0; index < 10; index++) {
            suffix.append(randomAlphabetCharacter());
        }
        return suffix.toString();
    }

    private static char randomAlphabetCharacter() {
        return PAIRING_CODE_ALPHABET.charAt(ThreadLocalRandom.current().nextInt(PAIRING_CODE_ALPHABET.length()));
    }
}
