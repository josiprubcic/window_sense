package com.windowsense.service;

import com.windowsense.exception.ConflictException;
import com.windowsense.service.PhysicalDevicePairingCodeHasher;
import com.windowsense.service.PhysicalDeviceRegistrationService;
import com.windowsense.entity.PhysicalDeviceRegistry;
import com.windowsense.repository.PhysicalDeviceRegistryRepository;
import com.windowsense.entity.PhysicalDeviceRegistryStatus;
import com.windowsense.service.PhysicalDeviceSecretHasher;
import com.windowsense.dto.RegisterPhysicalEspTokenRequest;
import com.windowsense.dto.RegisterPhysicalEspTokenOnlyRequest;
import com.windowsense.dto.RegisterPhysicalEspTokenOnlyResponse;
import com.windowsense.dto.RegisterPhysicalEspTokenResponse;
import com.windowsense.integration.thingsboard.PhysicalEspTokenRegistrationRequest;
import com.windowsense.integration.thingsboard.RegisteredPhysicalEspDevice;
import com.windowsense.integration.thingsboard.ThingsBoardProvisioningService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhysicalDeviceRegistrationServiceTest {

    private final PhysicalDeviceRegistryRepository registryRepository = mock(PhysicalDeviceRegistryRepository.class);
    private final ThingsBoardProvisioningService provisioningService = mock(ThingsBoardProvisioningService.class);
    private final PhysicalDeviceRegistrationService service = new PhysicalDeviceRegistrationService(
            registryRepository,
            provisioningService
    );

    @Test
    void registersEspWithHardcodedTokenWithoutReturningToken() {
        when(provisioningService.registerPhysicalEspDeviceWithToken(any(PhysicalEspTokenRegistrationRequest.class)))
                .thenReturn(new RegisteredPhysicalEspDevice("tb-device-id"));

        RegisterPhysicalEspTokenResponse response = service.registerWithHardcodedToken(new RegisterPhysicalEspTokenRequest(
                "ESP32 - Kuhinja",
                "WS-ESP32-0001",
                "esp32-chip-id",
                "1.0.0",
                List.of("window", "blinds"),
                "WS-DEMO-0001",
                "hardcoded-token-on-esp"
        ));

        assertThat(response.tbDeviceId()).isEqualTo("tb-device-id");
        assertThat(response.status()).isEqualTo(PhysicalDeviceRegistryStatus.CLAIMABLE.name());
        assertThat(Arrays.stream(RegisterPhysicalEspTokenResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("thingsBoardAccessToken", "accessToken", "tbDeviceAccessToken");

        ArgumentCaptor<PhysicalEspTokenRegistrationRequest> tbRequest = ArgumentCaptor.forClass(PhysicalEspTokenRegistrationRequest.class);
        verify(provisioningService).registerPhysicalEspDeviceWithToken(tbRequest.capture());
        assertThat(tbRequest.getValue().thingsBoardAccessToken()).isEqualTo("hardcoded-token-on-esp");

        ArgumentCaptor<PhysicalDeviceRegistry> registryCaptor = ArgumentCaptor.forClass(PhysicalDeviceRegistry.class);
        verify(registryRepository).save(registryCaptor.capture());
        assertThat(registryCaptor.getValue().getSerialNumber()).isEqualTo("WS-ESP32-0001");
        assertThat(registryCaptor.getValue().getTbDeviceId()).isEqualTo("tb-device-id");
        assertThat(registryCaptor.getValue().getPairingCodeHash()).isEqualTo(PhysicalDevicePairingCodeHasher.hash("WS-DEMO-0001"));
        assertThat(registryCaptor.getValue().getThingsBoardAccessTokenHash()).isEqualTo(PhysicalDeviceSecretHasher.hash("hardcoded-token-on-esp"));
        assertThat(registryCaptor.getValue().getStatus()).isEqualTo(PhysicalDeviceRegistryStatus.CLAIMABLE);
        assertThat(registryCaptor.getValue().getTbDeviceId()).doesNotContain("hardcoded-token-on-esp");
    }

    @Test
    void rejectsDuplicateHardcodedTokenByHash() {
        String tokenHash = PhysicalDeviceSecretHasher.hash("hardcoded-token-on-esp");
        when(registryRepository.existsByThingsBoardAccessTokenHash(tokenHash)).thenReturn(true);

        assertThatThrownBy(() -> service.registerWithHardcodedToken(new RegisterPhysicalEspTokenRequest(
                "ESP32 - Kuhinja",
                "WS-ESP32-0001",
                "esp32-chip-id",
                "1.0.0",
                List.of("window", "blinds"),
                "WS-DEMO-0001",
                "hardcoded-token-on-esp"
        ))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("ThingsBoard access tokenom vec postoji");

        verify(provisioningService, never()).registerPhysicalEspDeviceWithToken(any());
        verify(registryRepository, never()).save(any());
    }

    @Test
    void registersEspFromTokenOnlyAndReturnsGeneratedPairingCode() {
        when(provisioningService.registerPhysicalEspDeviceWithToken(any(PhysicalEspTokenRegistrationRequest.class)))
                .thenReturn(new RegisteredPhysicalEspDevice("tb-device-id"));

        RegisterPhysicalEspTokenOnlyResponse response = service.registerWithHardcodedTokenOnly(
                new RegisterPhysicalEspTokenOnlyRequest("roleta1")
        );

        assertThat(response.tbDeviceId()).isEqualTo("tb-device-id");
        assertThat(response.deviceName()).startsWith("WindowSense ESP32 ");
        assertThat(response.serialNumber()).startsWith("WS-ESP32-");
        assertThat(response.serialNumber()).doesNotContain(PhysicalDeviceSecretHasher.hash("roleta1").substring(0, 12).toUpperCase());
        assertThat(response.pairingCode()).startsWith("WS-");
        assertThat(response.status()).isEqualTo(PhysicalDeviceRegistryStatus.CLAIMABLE.name());
        assertThat(Arrays.stream(RegisterPhysicalEspTokenOnlyResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("thingsBoardAccessToken", "accessToken", "tbDeviceAccessToken");

        ArgumentCaptor<PhysicalEspTokenRegistrationRequest> tbRequest = ArgumentCaptor.forClass(PhysicalEspTokenRegistrationRequest.class);
        verify(provisioningService).registerPhysicalEspDeviceWithToken(tbRequest.capture());
        assertThat(tbRequest.getValue().thingsBoardAccessToken()).isEqualTo("roleta1");

        ArgumentCaptor<PhysicalDeviceRegistry> registryCaptor = ArgumentCaptor.forClass(PhysicalDeviceRegistry.class);
        verify(registryRepository).save(registryCaptor.capture());
        assertThat(registryCaptor.getValue().getPairingCodeHash()).isEqualTo(PhysicalDevicePairingCodeHasher.hash(response.pairingCode()));
        assertThat(registryCaptor.getValue().getThingsBoardAccessTokenHash()).isEqualTo(PhysicalDeviceSecretHasher.hash("roleta1"));
    }
}
