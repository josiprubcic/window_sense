package com.windowsense.service;

import com.windowsense.config.WindowSenseProperties;
import com.windowsense.entity.DeviceCapability;
import com.windowsense.entity.WindowDevice;
import com.windowsense.dto.CommandRequest;
import com.windowsense.service.CommandResult;
import com.windowsense.entity.RuntimeState;
import com.windowsense.repository.RuntimeStateRepository;
import com.windowsense.service.CommandService;
import com.windowsense.service.EventLogService;
import com.windowsense.mapper.RoomCommandRpcMapper;
import com.windowsense.integration.thingsboard.ThingsBoardRpcRequest;
import com.windowsense.integration.thingsboard.ThingsBoardRpcResult;
import com.windowsense.integration.thingsboard.ThingsBoardRpcService;
import com.windowsense.service.VirtualThingsBoardRpcCommandDeliveryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VirtualThingsBoardRpcCommandDeliveryPortTest {

    private final CommandService commandService = mock(CommandService.class);
    private final ThingsBoardRpcService rpcService = mock(ThingsBoardRpcService.class);
    private final WindowSenseProperties properties = rpcProperties();
    private final RuntimeStateRepository runtimeStateRepository = new RuntimeStateRepository();
    private final VirtualThingsBoardRpcCommandDeliveryPort port = new VirtualThingsBoardRpcCommandDeliveryPort(
            commandService,
            new RoomCommandRpcMapper(),
            rpcService,
            properties,
            runtimeStateRepository,
            new EventLogService()
    );

    @Test
    void sendsVirtualCommandThroughThingsBoardRpc() {
        WindowDevice device = WindowDevice.virtualDevice(
                "Virtualni roleta",
                "tb-blinds-id",
                Set.of(DeviceCapability.BLINDS_CONTROL)
        );
        ReflectionTestUtils.setField(device, "id", UUID.randomUUID());
        RuntimeState.Command prepared = new RuntimeState.Command("tb-blinds-id", "blinds", "setPosition", 85.0, "room-dashboard");
        when(commandService.prepareDeviceCommand(eq("tb-blinds-id"), any(CommandRequest.class)))
                .thenReturn(CommandResult.command("blinds", "setPosition", 85.0, prepared));
        when(rpcService.sendTwoWayRpc(eq("tb-blinds-id"), any(ThingsBoardRpcRequest.class)))
                .thenReturn(ThingsBoardRpcResult.executed(Map.of("status", "EXECUTED", "target", "blinds")));

        var response = port.deliver(
                UUID.randomUUID(),
                device,
                new CommandRequest("blinds", "setPosition", 85.0, "room-dashboard")
        );

        assertThat(response.delivery()).isEqualTo("THINGSBOARD_RPC");
        assertThat(response.status()).isEqualTo("EXECUTED");
        assertThat(response.deviceResponse()).containsEntry("target", "blinds");

        ArgumentCaptor<ThingsBoardRpcRequest> requestCaptor = ArgumentCaptor.forClass(ThingsBoardRpcRequest.class);
        verify(rpcService).sendTwoWayRpc(eq("tb-blinds-id"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().method()).isEqualTo("setAngle");
        assertThat(requestCaptor.getValue().params()).isEqualTo(76.5);
        assertThat(runtimeStateRepository.getState().commandQueue).isEmpty();
    }

    private static WindowSenseProperties rpcProperties() {
        WindowSenseProperties properties = new WindowSenseProperties();
        properties.getThingsBoard().setHost("http://thingsboard.local");
        properties.getThingsBoard().setProvisioningAuthMode(WindowSenseProperties.ProvisioningAuthMode.JWT);
        properties.getThingsBoard().setJwtToken("test-jwt");
        properties.getVirtualSimulator().setMqttRpcEnabled(true);
        properties.getCommands().getRpc().setEnabled(true);
        properties.getCommands().getRpc().setTimeoutMs(15000);
        properties.getCommands().getRpc().setPersistent(false);
        return properties;
    }
}
