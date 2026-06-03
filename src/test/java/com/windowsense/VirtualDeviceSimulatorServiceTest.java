package com.windowsense;

import com.windowsense.device.DeviceStatus;
import com.windowsense.device.DeviceType;
import com.windowsense.device.WindowDevice;
import com.windowsense.device.WindowDeviceRepository;
import com.windowsense.home.Home;
import com.windowsense.room.Room;
import com.windowsense.user.AppUser;
import com.windowsense.virtual.TelemetryPublisher;
import com.windowsense.virtual.VirtualDeviceSimulatorService;
import com.windowsense.virtual.VirtualWeatherDataService;
import com.windowsense.virtual.VirtualWeatherSample;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VirtualDeviceSimulatorServiceTest {

    private final WindowDeviceRepository windowDeviceRepository = mock(WindowDeviceRepository.class);
    private final VirtualWeatherDataService virtualWeatherDataService = mock(VirtualWeatherDataService.class);
    private final TelemetryPublisher telemetryPublisher = mock(TelemetryPublisher.class);
    private final VirtualDeviceSimulatorService simulatorService = new VirtualDeviceSimulatorService(
            windowDeviceRepository,
            virtualWeatherDataService,
            telemetryPublisher
    );

    @Test
    void sendsTelemetryOnlyForActiveVirtualDevices() {
        WindowDevice activeVirtual = device("Kuhinja", DeviceType.VIRTUAL, true, DeviceStatus.ACTIVE);
        WindowDevice inactiveVirtual = device("Spavaca soba", DeviceType.VIRTUAL, true, DeviceStatus.INACTIVE);
        WindowDevice physical = device("Fizicki prozor", DeviceType.PHYSICAL, false, DeviceStatus.ACTIVE);
        when(windowDeviceRepository.findActiveVirtualDevicesWithRoom())
                .thenReturn(List.of(activeVirtual, inactiveVirtual, physical));
        when(virtualWeatherDataService.samples()).thenReturn(List.of(sample(false, 0, 12, 52000, 23.5, 8, 72, 20)));

        simulatorService.publishVirtualTelemetry();

        verify(telemetryPublisher).publishTelemetry(org.mockito.Mockito.eq(activeVirtual), any());
        verify(telemetryPublisher, never()).publishTelemetry(org.mockito.Mockito.eq(inactiveVirtual), any());
        verify(telemetryPublisher, never()).publishTelemetry(org.mockito.Mockito.eq(physical), any());
    }

    @Test
    void usesWeatherCsvSampleForPayload() {
        WindowDevice activeVirtual = device("Kuhinja", DeviceType.VIRTUAL, true, DeviceStatus.ACTIVE);
        when(windowDeviceRepository.findActiveVirtualDevicesWithRoom()).thenReturn(List.of(activeVirtual));
        when(virtualWeatherDataService.samples()).thenReturn(List.of(sample(true, 47, 79, 12300, 22.7, 31, 26, 86)));

        simulatorService.publishVirtualTelemetry();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> payloadCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(telemetryPublisher).publishTelemetry(org.mockito.Mockito.eq(activeVirtual), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload)
                .containsEntry("rainDetected", true)
                .containsEntry("rainIntensity", 47)
                .containsEntry("rainRiskPercent", 79)
                .containsEntry("lux", 12300)
                .containsEntry("indoorTempC", 22.7)
                .containsEntry("windKmh", 31)
                .containsEntry("windowOpenPercent", 26)
                .containsEntry("blindClosedPercent", 86)
                .containsEntry("roomName", "Kuhinja")
                .containsEntry("isVirtual", true);
    }

    private static VirtualWeatherSample sample(
            boolean rainDetected,
            int rainIntensity,
            int rainRiskPercent,
            int lux,
            double indoorTempC,
            int windKmh,
            int windowOpenPercent,
            int blindClosedPercent
    ) {
        return new VirtualWeatherSample(
                rainDetected,
                rainIntensity,
                rainRiskPercent,
                lux,
                indoorTempC,
                windKmh,
                windowOpenPercent,
                blindClosedPercent
        );
    }

    private static WindowDevice device(String roomName, DeviceType deviceType, boolean virtual, DeviceStatus status) {
        AppUser user = new AppUser("auth0|window-user", "user@example.com", "Window User");
        Home home = new Home(user, "Default Home");
        Room room = new Room(home, roomName, "asset-id-" + roomName);
        ReflectionTestUtils.setField(room, "id", UUID.randomUUID());
        WindowDevice device = WindowDevice.virtualDevice("WindowSense - " + roomName, "device-id-" + roomName);
        ReflectionTestUtils.setField(device, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(device, "deviceType", deviceType);
        ReflectionTestUtils.setField(device, "virtual", virtual);
        ReflectionTestUtils.setField(device, "status", status);
        room.addDevice(device);
        return device;
    }
}
