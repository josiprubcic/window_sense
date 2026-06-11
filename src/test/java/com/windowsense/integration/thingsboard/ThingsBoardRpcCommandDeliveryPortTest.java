package com.windowsense.integration.thingsboard;

import com.windowsense.config.WindowSenseProperties;
import com.windowsense.entity.WindowDevice;
import com.windowsense.dto.CommandRequest;
import com.windowsense.service.CommandResult;
import com.windowsense.entity.RuntimeState;
import com.windowsense.repository.RuntimeStateRepository;
import com.windowsense.service.CommandService;
import com.windowsense.service.EventLogService;
import com.windowsense.mapper.RoomCommandRpcMapper;
import com.windowsense.integration.thingsboard.ThingsBoardRpcCommandDeliveryPort;
import com.windowsense.integration.thingsboard.ThingsBoardRpcRequest;
import com.windowsense.integration.thingsboard.ThingsBoardRpcResult;
import com.windowsense.integration.thingsboard.ThingsBoardRpcService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThingsBoardRpcCommandDeliveryPortTest {

    private final CommandService commandService = mock(CommandService.class);
    private final ThingsBoardRpcService rpcService = mock(ThingsBoardRpcService.class);
    private final WindowSenseProperties properties = rpcProperties();
    private final RuntimeStateRepository runtimeStateRepository = new RuntimeStateRepository();
    private final ThingsBoardRpcCommandDeliveryPort port = new ThingsBoardRpcCommandDeliveryPort(
            commandService,
            new RoomCommandRpcMapper(),
            rpcService,
            properties,
            runtimeStateRepository,
            new EventLogService()
    );

    @Test
    void sendsRpcToTargetDeviceTbDeviceId() {
        RuntimeState.Command prepared = new RuntimeState.Command("tb-device-123", "window", "close", 0.0, "test");
        when(commandService.prepareDeviceCommand(eq("tb-device-123"), any(CommandRequest.class)))
                .thenReturn(CommandResult.command("window", "close", 0.0, prepared));
        when(rpcService.sendTwoWayRpc(eq("tb-device-123"), any(ThingsBoardRpcRequest.class)))
                .thenReturn(ThingsBoardRpcResult.executed(Map.of("status", "EXECUTED", "windowPosition", 0)));

        var response = port.deliver(
                java.util.UUID.randomUUID(),
                WindowDevice.physicalDevice("ESP32", "tb-device-123"),
                new CommandRequest("window", "close", null, "test")
        );

        assertThat(response.status()).isEqualTo("EXECUTED");
        assertThat(response.delivery()).isEqualTo("THINGSBOARD_RPC");
        assertThat(response.tbDeviceId()).isEqualTo("tb-device-123");
        assertThat(response.deviceResponse()).containsEntry("windowPosition", 0);

        ArgumentCaptor<ThingsBoardRpcRequest> requestCaptor = ArgumentCaptor.forClass(ThingsBoardRpcRequest.class);
        verify(rpcService).sendTwoWayRpc(eq("tb-device-123"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().method()).isEqualTo("closeWindow");
        assertThat(requestCaptor.getValue().params()).isEqualTo(java.util.Map.of());
        assertThat(requestCaptor.getValue().timeout()).isEqualTo(15000);
        assertThat(requestCaptor.getValue().persistent()).isFalse();
        assertThat(runtimeStateRepository.getState().commandQueue).isEmpty();
    }

    @Test
    void mapsTimeoutAndFailedStatusesWithoutQueueing() {
        RuntimeState.Command timeoutCommand = new RuntimeState.Command("tb-device-123", "blinds", "open", 0.0, "test");
        when(commandService.prepareDeviceCommand(eq("tb-device-123"), any(CommandRequest.class)))
                .thenReturn(CommandResult.command("blinds", "open", 0.0, timeoutCommand));
        when(rpcService.sendTwoWayRpc(eq("tb-device-123"), any(ThingsBoardRpcRequest.class)))
                .thenReturn(ThingsBoardRpcResult.timeout());

        var timeout = port.deliver(
                java.util.UUID.randomUUID(),
                WindowDevice.physicalDevice("ESP32", "tb-device-123"),
                new CommandRequest("blinds", "open", null, "test")
        );

        assertThat(timeout.status()).isEqualTo("TIMEOUT");
        assertThat(runtimeStateRepository.getState().commandQueue).isEmpty();

        RuntimeState.Command failedCommand = new RuntimeState.Command("tb-device-123", "blinds", "close", 100.0, "test");
        when(commandService.prepareDeviceCommand(eq("tb-device-123"), any(CommandRequest.class)))
                .thenReturn(CommandResult.command("blinds", "close", 100.0, failedCommand));
        when(rpcService.sendTwoWayRpc(eq("tb-device-123"), any(ThingsBoardRpcRequest.class)))
                .thenReturn(ThingsBoardRpcResult.failed("HTTP 500"));

        var failed = port.deliver(
                java.util.UUID.randomUUID(),
                WindowDevice.physicalDevice("ESP32", "tb-device-123"),
                new CommandRequest("blinds", "close", null, "test")
        );

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.deviceResponse()).containsEntry("error", "HTTP 500");
        assertThat(runtimeStateRepository.getState().commandQueue).isEmpty();
    }

    @Test
    void rejectsMissingTbDeviceIdAndUnconfiguredRpc() {
        assertThatThrownBy(() -> port.deliver(
                java.util.UUID.randomUUID(),
                WindowDevice.physicalDevice("ESP32", " "),
                new CommandRequest("window", "close", null, "test")
        ))
                .hasMessage("THINGSBOARD_DEVICE_ID_REQUIRED");
        verify(rpcService, never()).sendTwoWayRpc(any(), any());

        WindowSenseProperties disabledProperties = rpcProperties();
        disabledProperties.getCommands().getRpc().setEnabled(false);
        ThingsBoardRpcCommandDeliveryPort disabledPort = new ThingsBoardRpcCommandDeliveryPort(
                commandService,
                new RoomCommandRpcMapper(),
                rpcService,
                disabledProperties,
                runtimeStateRepository,
                new EventLogService()
        );

        assertThatThrownBy(() -> disabledPort.deliver(
                java.util.UUID.randomUUID(),
                WindowDevice.physicalDevice("ESP32", "tb-device-123"),
                new CommandRequest("window", "close", null, "test")
        ))
                .hasMessage("THINGSBOARD_RPC_NOT_CONFIGURED");
    }

    private static WindowSenseProperties rpcProperties() {
        WindowSenseProperties properties = new WindowSenseProperties();
        properties.getThingsBoard().setHost("http://thingsboard.local");
        properties.getThingsBoard().setUsername("tenant@example.com");
        properties.getThingsBoard().setPassword("tenant-password");
        properties.getCommands().getRpc().setEnabled(true);
        properties.getCommands().getRpc().setTimeoutMs(15000);
        properties.getCommands().getRpc().setPersistent(false);
        return properties;
    }
}
