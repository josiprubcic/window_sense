package com.windowsense.service;

import com.windowsense.service.AutomationService;
import com.windowsense.entity.DeviceCapability;
import com.windowsense.entity.WindowDevice;
import com.windowsense.entity.Home;
import com.windowsense.dto.CommandRequest;
import com.windowsense.repository.RuntimeStateRepository;
import com.windowsense.entity.Room;
import com.windowsense.service.RoomAutomationEvaluation;
import com.windowsense.service.RoomAutomationEvaluator;
import com.windowsense.service.CommandService;
import com.windowsense.service.EventLogService;
import com.windowsense.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RoomAutomationEvaluatorTest {

    private final CommandService commandService = mock(CommandService.class);
    private final RuntimeStateRepository runtimeStateRepository = new RuntimeStateRepository();
    private final RoomAutomationEvaluator evaluator = new RoomAutomationEvaluator(
            new AutomationService(),
            commandService,
            new EventLogService(),
            runtimeStateRepository
    );

    @Test
    void virtualAutomationUpdatesVirtualActuatorStateImmediately() {
        Room room = room("Demo");
        WindowDevice device = WindowDevice.virtualDevice(
                "WindowSense - Demo",
                "virtual-device-id",
                Set.of(DeviceCapability.WINDOW_CONTROL)
        );
        room.addDevice(device);
        device.updateSimulationTelemetry(false, 0, 90, 12000, 23, 0, 65, 40);

        RoomAutomationEvaluation evaluation = evaluator.evaluateAndApply(
                room,
                device,
                evaluator.thresholds(room),
                evaluator.virtualTelemetry(device)
        );

        assertThat(evaluation.decisions()).hasSize(1);
        assertThat(evaluation.decisions().getFirst().target()).isEqualTo("window");
        assertThat(device.getSimWindowOpenPercent()).isZero();
        assertThat(evaluation.telemetry()).containsEntry("windowOpenPercent", 0.0);
        verify(commandService, never()).enqueueDeviceCommand(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void physicalAutomationQueuesCommandInsteadOfMutatingState() {
        Room room = room("Kuhinja");
        WindowDevice device = WindowDevice.physicalDevice("ESP32 - Kuhinja", "physical-device-id");
        room.addDevice(device);
        device.updateSimulationTelemetry(false, 0, 10, 1000, 20, 0, 44, 10);

        RoomAutomationEvaluation evaluation = evaluator.evaluateAndApply(
                room,
                device,
                evaluator.thresholds(room),
                Map.of(
                        "rainDetected", true,
                        "rainIntensity", 80,
                        "rainRiskPercent", 90,
                        "lux", 12000,
                        "indoorTempC", 23,
                        "windKmh", 0,
                        "windowOpenPercent", 72,
                        "blindClosedPercent", 85,
                        "day", 1
                )
        );

        ArgumentCaptor<CommandRequest> requestCaptor = ArgumentCaptor.forClass(CommandRequest.class);
        verify(commandService).enqueueDeviceCommand(org.mockito.Mockito.eq("physical-device-id"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().target()).isEqualTo("window");
        assertThat(requestCaptor.getValue().action()).isEqualTo("close");
        assertThat(evaluation.decisions()).hasSize(1);
        assertThat(device.getSimWindowOpenPercent()).isEqualTo(44);
    }

    @Test
    void automationDecisionIsWrittenToEventLog() {
        Room room = room("Dnevni boravak");
        WindowDevice device = WindowDevice.virtualDevice("Virtualni uredjaj", "virtual-device-id");
        room.addDevice(device);
        device.updateSimulationTelemetry(false, 0, 10, 80000, 27, 0, 0, 10);

        RoomAutomationEvaluation evaluation = evaluator.evaluateAndApply(
                room,
                device,
                evaluator.thresholds(room),
                evaluator.virtualTelemetry(device)
        );

        assertThat(evaluation.decisions()).hasSize(1);
        assertThat(evaluation.decisions().getFirst().target()).isEqualTo("blinds");
        assertThat(runtimeStateRepository.getState().events)
                .anySatisfy(event -> {
                    assertThat(event.source).isEqualTo("room-automation");
                    assertThat(event.title).isEqualTo("Automatska odluka: rolete/postavi");
                    assertThat(event.details).contains("Dan je aktivan");
                });
    }

    @Test
    void automationIgnoresBlindsDecisionForWindowOnlyDevice() {
        Room room = room("Dnevni boravak");
        WindowDevice device = WindowDevice.virtualDevice(
                "Virtualni prozor",
                "virtual-window-id",
                Set.of(DeviceCapability.WINDOW_CONTROL)
        );
        room.addDevice(device);
        device.updateSimulationTelemetry(false, 0, 10, 80000, 27, 0, 100, 10);

        RoomAutomationEvaluation evaluation = evaluator.evaluateAndApply(
                room,
                device,
                evaluator.thresholds(room),
                evaluator.virtualTelemetry(device)
        );

        assertThat(evaluation.decisions()).isEmpty();
        assertThat(device.getSimBlindClosedPercent()).isEqualTo(10);
        assertThat(runtimeStateRepository.getState().events)
                .noneSatisfy(event -> assertThat(event.details).contains("Virtualni prozor"));
    }

    @Test
    void automationIgnoresWindowDecisionForBlindsOnlyDevice() {
        Room room = room("Dnevni boravak");
        WindowDevice device = WindowDevice.virtualDevice(
                "Virtualni roleta",
                "virtual-blinds-id",
                Set.of(DeviceCapability.BLINDS_CONTROL)
        );
        room.addDevice(device);
        device.updateSimulationTelemetry(true, 90, 100, 12000, 23, 0, 65, 85, 1);

        RoomAutomationEvaluation evaluation = evaluator.evaluateAndApply(
                room,
                device,
                evaluator.thresholds(room),
                evaluator.virtualTelemetry(device)
        );

        assertThat(evaluation.decisions()).isEmpty();
        assertThat(device.getSimWindowOpenPercent()).isEqualTo(65);
        assertThat(runtimeStateRepository.getState().events)
                .noneSatisfy(event -> assertThat(event.details).contains("Virtualni roleta"));
    }

    private static Room room(String name) {
        AppUser user = new AppUser("auth0|window-user", "user@example.com", "Window User");
        Home home = new Home(user, "Default Home");
        return new Room(home, name, "asset-" + name);
    }
}
