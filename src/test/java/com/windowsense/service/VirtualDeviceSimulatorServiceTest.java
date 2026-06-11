package com.windowsense.service;

import com.windowsense.service.AutomationService;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.entity.DeviceStatus;
import com.windowsense.entity.DeviceType;
import com.windowsense.entity.SimulationMode;
import com.windowsense.entity.WindowDevice;
import com.windowsense.integration.thingsboard.ThingsBoardTelemetryQueryService;
import com.windowsense.integration.thingsboard.ThingsBoardProvisioningService;
import com.windowsense.repository.RuntimeStateRepository;
import com.windowsense.repository.WindowDeviceRepository;
import com.windowsense.entity.Home;
import com.windowsense.entity.Room;
import com.windowsense.service.RoomAutomationEvaluator;
import com.windowsense.entity.AppUser;
import com.windowsense.service.CommandService;
import com.windowsense.service.TelemetryPublisher;
import com.windowsense.service.VirtualDeviceSimulatorService;
import com.windowsense.service.VirtualWeatherDataService;
import com.windowsense.service.VirtualWeatherSample;
import org.junit.jupiter.api.Tag;
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
    private final CommandService commandService = mock(CommandService.class);
    private final ThingsBoardProvisioningService provisioningService = mock(ThingsBoardProvisioningService.class);
    private final ThingsBoardTelemetryQueryService thingsBoardTelemetryQueryService = mock(ThingsBoardTelemetryQueryService.class);
    private final RuntimeStateRepository runtimeStateRepository = new RuntimeStateRepository();
    private final VirtualDeviceSimulatorService simulatorService = new VirtualDeviceSimulatorService(
            windowDeviceRepository,
            virtualWeatherDataService,
            telemetryPublisher,
            new RoomAutomationEvaluator(new AutomationService(), commandService),
            provisioningService,
            thingsBoardTelemetryQueryService,
            new EventLogService(),
            runtimeStateRepository,
            new WindowSenseProperties()
    );

    @Test
    @Tag("core")
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
        assertThat(activeVirtual.getSimRainRiskPercent()).isEqualTo(12);
        assertThat(activeVirtual.getSimWindowOpenPercent()).isEqualTo(72);
        assertThat(activeVirtual.getSimBlindClosedPercent()).isEqualTo(20);
    }

    @Test
    void skipsManualVirtualDevices() {
        WindowDevice manualVirtual = device("Kuhinja", DeviceType.VIRTUAL, true, DeviceStatus.ACTIVE);
        manualVirtual.updateSimulationMode(SimulationMode.MANUAL);
        when(windowDeviceRepository.findActiveVirtualDevicesWithRoom()).thenReturn(List.of(manualVirtual));
        when(virtualWeatherDataService.samples()).thenReturn(List.of(sample(true, 47, 79, 12300, 22.7, 31, 26, 86)));

        simulatorService.publishVirtualTelemetry();

        verify(telemetryPublisher, never()).publishTelemetry(any(), any());
        assertThat(manualVirtual.getSimRainRiskPercent()).isEqualTo(12);
        assertThat(manualVirtual.getSimulationMode()).isEqualTo(SimulationMode.MANUAL);
    }

    @Test
    void usesWeatherCsvSampleForPayload() {
        WindowDevice activeVirtual = device("Kuhinja", DeviceType.VIRTUAL, true, DeviceStatus.ACTIVE);
        when(windowDeviceRepository.findActiveVirtualDevicesWithRoom()).thenReturn(List.of(activeVirtual));
        when(virtualWeatherDataService.samples()).thenReturn(List.of(sample(false, 0, 10, 12300, 22.7, 31, 26, 86)));

        simulatorService.publishVirtualTelemetry();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> payloadCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(telemetryPublisher).publishTelemetry(org.mockito.Mockito.eq(activeVirtual), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload)
                .containsEntry("rainDetected", false)
                .containsEntry("rainIntensity", 0.0)
                .containsEntry("rainRiskPercent", 10.0)
                .containsEntry("windKmh", 31.0)
                .containsEntry("windowOpenPercent", 72.0)
                .containsEntry("blindClosedPercent", 20.0)
                .containsEntry("roomName", "Kuhinja")
                .containsEntry("isVirtual", true);
        assertThat(activeVirtual.getSimWindowOpenPercent()).isEqualTo(72);
        assertThat(activeVirtual.getSimBlindClosedPercent()).isEqualTo(20);
    }

    @Test
    @Tag("core")
    void recordsRoomEventForVirtualTelemetryTick() {
        WindowDevice activeVirtual = device("Kuhinja", DeviceType.VIRTUAL, true, DeviceStatus.ACTIVE);
        when(windowDeviceRepository.findActiveVirtualDevicesWithRoom()).thenReturn(List.of(activeVirtual));
        when(virtualWeatherDataService.samples()).thenReturn(List.of(sample(false, 0, 10, 12300, 22.7, 31, 26, 86)));

        simulatorService.publishVirtualTelemetry();

        var events = runtimeStateRepository.getState().events;
        assertThat(events.getFirst().source).isEqualTo("virtual-simulator");
        assertThat(events.getFirst().roomId).isEqualTo(activeVirtual.getRoom().getId().toString());
        assertThat(events.getFirst().deviceId).isEqualTo(activeVirtual.getId().toString());
        assertThat(events.getFirst().title).isEqualTo("Virtualna telemetrija osvjezena");
    }

    @Test
    void usesSameWeatherSampleForDevicesInSameHome() {
        AppUser user = new AppUser("auth0|window-user", "user@example.com", "Window User");
        Home home = new Home(user, "Default Home");
        ReflectionTestUtils.setField(home, "id", UUID.randomUUID());
        WindowDevice kitchen = deviceInHome(home, "Kuhinja");
        WindowDevice bedroom = deviceInHome(home, "Spavaca soba");
        when(windowDeviceRepository.findActiveVirtualDevicesWithRoom()).thenReturn(List.of(kitchen, bedroom));
        when(virtualWeatherDataService.samples()).thenReturn(List.of(
                sample(false, 0, 10, 12300, 22.7, 31, 26, 86),
                sample(true, 80, 90, 7000, 18.4, 74, 10, 40)
        ));

        simulatorService.publishVirtualTelemetry();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> payloadCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(telemetryPublisher, org.mockito.Mockito.times(2)).publishTelemetry(any(), payloadCaptor.capture());
        List<Map<String, Object>> payloads = payloadCaptor.getAllValues();
        assertThat(payloads.get(0).get("rainDetected")).isEqualTo(payloads.get(1).get("rainDetected"));
        assertThat(payloads.get(0).get("rainRiskPercent")).isEqualTo(payloads.get(1).get("rainRiskPercent"));
        assertThat(payloads.get(0).get("windKmh")).isEqualTo(payloads.get(1).get("windKmh"));
    }

    @Test
    void includesAutomationDecisionInPayloadWhenRuleApplies() {
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
                .containsEntry("windowOpenPercent", 0.0);
        assertThat(activeVirtual.getSimWindowOpenPercent()).isEqualTo(0);
    }

    @Test
    @Tag("core")
    void publishesWeatherOnlyForPhysicalDevicesWhenEnabled() {
        WindowSenseProperties properties = new WindowSenseProperties();
        properties.getVirtualSimulator().setPublishToThingsBoard(true);
        properties.getVirtualSimulator().setPublishPhysicalWeatherToThingsBoard(true);
        VirtualDeviceSimulatorService service = new VirtualDeviceSimulatorService(
                windowDeviceRepository,
                virtualWeatherDataService,
                telemetryPublisher,
                new RoomAutomationEvaluator(new AutomationService(), commandService),
                provisioningService,
                thingsBoardTelemetryQueryService,
                new EventLogService(),
                new RuntimeStateRepository(),
                properties
        );
        WindowDevice physical = device("Dnevna soba", DeviceType.PHYSICAL, false, DeviceStatus.ACTIVE);
        physical.storeEncryptedThingsBoardDeviceToken("encrypted-token");
        when(windowDeviceRepository.findActiveVirtualDevicesWithRoom()).thenReturn(List.of());
        when(windowDeviceRepository.findActivePhysicalDevicesWithRoomAndToken()).thenReturn(List.of(physical));
        when(virtualWeatherDataService.samples()).thenReturn(List.of(sample(false, 0, 10, 12300, 22.7, 31, 26, 86)));
        when(thingsBoardTelemetryQueryService.latestDeviceTelemetry(physical.getTbDeviceId()))
                .thenReturn(new ThingsBoardTelemetryQueryService.LatestTelemetry(Map.of(), null));

        service.publishVirtualTelemetry();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> payloadCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(telemetryPublisher).publishTelemetry(org.mockito.Mockito.eq(physical), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload)
                .containsEntry("rainDetected", false)
                .containsEntry("rainIntensity", 0.0)
                .containsEntry("rainRiskPercent", 10.0)
                .containsEntry("windKmh", 31.0)
                .containsEntry("roomName", "Dnevna soba")
                .containsEntry("isVirtual", false)
                .containsEntry("simulationSource", "windowsense-backend")
                .doesNotContainKeys("windowOpenPercent", "blindClosedPercent");
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
        ReflectionTestUtils.setField(home, "id", UUID.randomUUID());
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

    private static WindowDevice deviceInHome(Home home, String roomName) {
        Room room = new Room(home, roomName, "asset-id-" + roomName);
        ReflectionTestUtils.setField(room, "id", UUID.randomUUID());
        WindowDevice device = WindowDevice.virtualDevice("WindowSense - " + roomName, "device-id-" + roomName);
        ReflectionTestUtils.setField(device, "id", UUID.randomUUID());
        room.addDevice(device);
        return device;
    }
}
