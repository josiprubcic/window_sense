package com.windowsense.service;

import com.windowsense.service.AutomationService;
import com.windowsense.exception.ConflictException;
import com.windowsense.entity.DeviceCapability;
import com.windowsense.entity.WindowDevice;
import com.windowsense.repository.WindowDeviceRepository;
import com.windowsense.entity.Home;
import com.windowsense.repository.RuntimeStateRepository;
import com.windowsense.entity.Room;
import com.windowsense.service.RoomAutomationEvaluator;
import com.windowsense.service.CommandService;
import com.windowsense.service.EventLogService;
import com.windowsense.entity.AppUser;
import com.windowsense.service.VirtualDeviceRpcHandler;
import com.windowsense.service.VirtualDeviceRpcResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VirtualDeviceRpcHandlerTest {

    private final WindowDeviceRepository windowDeviceRepository = mock(WindowDeviceRepository.class);
    private final RuntimeStateRepository runtimeStateRepository = new RuntimeStateRepository();
    private final VirtualDeviceRpcHandler handler = new VirtualDeviceRpcHandler(
            windowDeviceRepository,
            new RoomAutomationEvaluator(
                    new AutomationService(),
                    mock(CommandService.class),
                    new EventLogService(),
                    runtimeStateRepository
            ),
            runtimeStateRepository,
            new EventLogService()
    );

    @Test
    void appliesBlindsRpcAndReturnsBlindsOnlyTelemetry() {
        WindowDevice device = virtualDevice("Virtualni roleta", Set.of(DeviceCapability.BLINDS_CONTROL));
        device.updateSimulationTelemetry(false, 0, 10, 42000, 24, 12, 65, 20);
        when(windowDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        VirtualDeviceRpcResult result = handler.handle(
                device.getId(),
                "setBlindsPosition",
                Map.of("position", 85)
        );

        assertThat(device.getSimBlindClosedPercent()).isEqualTo(85);
        assertThat(device.getSimWindowOpenPercent()).isEqualTo(65);
        assertThat(result.response())
                .containsEntry("status", "EXECUTED")
                .containsEntry("target", "blinds")
                .containsEntry("action", "setPosition")
                .containsEntry("position", 85.0);
        assertThat(result.changed()).isTrue();
        assertThat(result.telemetry())
                .containsEntry("blindClosedPercent", 85.0)
                .containsEntry("rainProbability", 10.0)
                .containsEntry("day", 1)
                .doesNotContainKey("windowOpenPercent");
        assertThat(runtimeStateRepository.getState().events)
                .anySatisfy(event -> {
                    assertThat(event.source).isEqualTo("thingsboard-rpc");
                    assertThat(event.title).isEqualTo("Rule chain odluka: blinds/setBlindsPosition");
                    assertThat(event.details)
                            .contains("ThingsBoard rule chain je izvrsio RPC komandu")
                            .contains("Akcija: postavi polozaj roleta na 85% zatvoreno")
                            .contains("day=1")
                            .contains("rainProbability=10%");
                });
    }

    @Test
    void logsDashboardRpcSeparatelyWhenCommandIdIsPresent() {
        WindowDevice device = virtualDevice("Virtualni roleta", Set.of(DeviceCapability.BLINDS_CONTROL));
        device.updateSimulationTelemetry(false, 0, 10, 42000, 24, 12, 65, 20);
        when(windowDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        handler.handle(
                device.getId(),
                "openBlinds",
                Map.of("commandId", "cmd-1", "position", 0)
        );

        assertThat(runtimeStateRepository.getState().events)
                .anySatisfy(event -> {
                    assertThat(event.source).isEqualTo("thingsboard-rpc");
                    assertThat(event.title).isEqualTo("Dashboard RPC izvrsen: blinds/openBlinds");
                    assertThat(event.details)
                            .contains("ThingsBoard je izvrsio dashboard/backend RPC komandu")
                            .contains("Akcija: podigni rolete na 0% zatvoreno")
                            .contains("rainProbability=10%");
                });
    }

    @Test
    void appliesSetAngleRpcToBlindsOnlyDeviceAsOpenAngle() {
        WindowDevice device = virtualDevice("Virtualni roleta", Set.of(DeviceCapability.BLINDS_CONTROL));
        device.updateSimulationTelemetry(false, 0, 10, 42000, 24, 12, 65, 20);
        when(windowDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        VirtualDeviceRpcResult result = handler.handle(
                device.getId(),
                "setAngle",
                Map.of("value", 90)
        );

        assertThat(device.getSimBlindClosedPercent()).isEqualTo(0.0);
        assertThat(device.getSimWindowOpenPercent()).isEqualTo(65.0);
        assertThat(result.response())
                .containsEntry("status", "EXECUTED")
                .containsEntry("target", "blinds")
                .containsEntry("action", "setAngle")
                .containsEntry("position", 0.0);
        assertThat(result.changed()).isTrue();
        assertThat(result.telemetry())
                .containsEntry("blindClosedPercent", 0.0)
                .doesNotContainKey("windowOpenPercent");
        assertThat(runtimeStateRepository.getState().events)
                .anySatisfy(event -> {
                    assertThat(event.title).isEqualTo("Rule chain odluka: blinds/setAngle");
                    assertThat(event.details)
                            .contains("Akcija: postavi kut rolete na 0% zatvoreno")
                            .contains("rainProbability=10%");
                });
    }

    @Test
    void ignoresSetAngleRpcWhenTargetPositionAlreadyApplied() {
        WindowDevice device = virtualDevice("Virtualni roleta", Set.of(DeviceCapability.BLINDS_CONTROL));
        device.updateSimulationTelemetry(false, 0, 10, 42000, 24, 12, 65, 0);
        when(windowDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        int eventCount = runtimeStateRepository.getState().events.size();

        VirtualDeviceRpcResult result = handler.handle(
                device.getId(),
                "setAngle",
                Map.of("value", 90)
        );

        assertThat(result.changed()).isFalse();
        assertThat(device.getSimBlindClosedPercent()).isEqualTo(0.0);
        assertThat(runtimeStateRepository.getState().events).hasSize(eventCount);
    }

    @Test
    void rejectsWindowRpcForBlindsOnlyDevice() {
        WindowDevice device = virtualDevice("Virtualni roleta", Set.of(DeviceCapability.BLINDS_CONTROL));
        when(windowDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> handler.handle(device.getId(), "openWindow", Map.of()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("DEVICE_DOES_NOT_SUPPORT_CAPABILITY");
    }

    private static WindowDevice virtualDevice(String name, Set<DeviceCapability> capabilities) {
        AppUser user = new AppUser("auth0|window-user", "user@example.com", "Window User");
        Home home = new Home(user, "Default Home");
        Room room = new Room(home, "Dnevni boravak", "tb-room-id");
        ReflectionTestUtils.setField(room, "id", UUID.randomUUID());
        WindowDevice device = WindowDevice.virtualDevice(name, "tb-device-id", capabilities);
        ReflectionTestUtils.setField(device, "id", UUID.randomUUID());
        room.addDevice(device);
        return device;
    }
}
