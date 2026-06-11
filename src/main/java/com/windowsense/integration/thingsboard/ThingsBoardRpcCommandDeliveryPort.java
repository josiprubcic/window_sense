package com.windowsense.integration.thingsboard;

import com.windowsense.exception.ConflictException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.entity.DeviceType;
import com.windowsense.entity.WindowDevice;
import com.windowsense.dto.CommandRequest;
import com.windowsense.service.CommandResult;
import com.windowsense.entity.RuntimeState;
import com.windowsense.repository.RuntimeStateRepository;
import com.windowsense.dto.RoomCommandResponse;
import com.windowsense.service.CommandDeliveryPort;
import com.windowsense.service.CommandService;
import com.windowsense.service.EventLogService;
import com.windowsense.mapper.MappedRoomCommandRpc;
import com.windowsense.mapper.RoomCommandRpcMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "windowsense.commands", name = "physical-delivery", havingValue = "thingsboard-rpc")
public class ThingsBoardRpcCommandDeliveryPort implements CommandDeliveryPort {

    private final CommandService commandService;
    private final RoomCommandRpcMapper rpcMapper;
    private final ThingsBoardRpcService rpcService;
    private final WindowSenseProperties properties;
    private final RuntimeStateRepository runtimeStateRepository;
    private final EventLogService eventLogService;
    private final ThingsBoardRpcResponseTelemetryPublisher telemetryPublisher;
    private final ThingsBoardProvisioningService provisioningService;

    public ThingsBoardRpcCommandDeliveryPort(
            CommandService commandService,
            RoomCommandRpcMapper rpcMapper,
            ThingsBoardRpcService rpcService,
            WindowSenseProperties properties,
            RuntimeStateRepository runtimeStateRepository,
            EventLogService eventLogService,
            ThingsBoardRpcResponseTelemetryPublisher telemetryPublisher,
            ThingsBoardProvisioningService provisioningService
    ) {
        this.commandService = commandService;
        this.rpcMapper = rpcMapper;
        this.rpcService = rpcService;
        this.properties = properties;
        this.runtimeStateRepository = runtimeStateRepository;
        this.eventLogService = eventLogService;
        this.telemetryPublisher = telemetryPublisher;
        this.provisioningService = provisioningService;
    }

    @Override
    public RoomCommandResponse deliver(UUID roomId, WindowDevice targetDevice, CommandRequest command) {
        if (targetDevice.getDeviceType() != DeviceType.PHYSICAL) {
            throw new IllegalArgumentException("ThingsBoard RPC podrzava samo fizicke uredjaje.");
        }
        if (targetDevice.getTbDeviceId() == null || targetDevice.getTbDeviceId().isBlank()) {
            throw new ConflictException("THINGSBOARD_DEVICE_ID_REQUIRED");
        }
        if (!properties.getCommands().getRpc().isEnabled() || !properties.getThingsBoard().isRestAuthReady()) {
            throw new ConflictException("THINGSBOARD_RPC_NOT_CONFIGURED");
        }

        CommandResult prepared = commandService.prepareDeviceCommand(targetDevice.getTbDeviceId(), command);
        RuntimeState.Command queued = prepared.queued;
        MappedRoomCommandRpc mapped = rpcMapper.toRpc(queued);
        syncManualDesiredAngle(targetDevice, mapped);
        ThingsBoardRpcResult result = rpcService.sendTwoWayRpc(
                targetDevice.getTbDeviceId(),
                new ThingsBoardRpcRequest(
                        mapped.method(),
                        mapped.params(),
                        properties.getCommands().getRpc().getTimeoutMs(),
                        properties.getCommands().getRpc().isPersistent()
                )
        );
        recordRpcEvent(roomId, targetDevice, queued, result);
        publishTelemetryFromRpcResponse(targetDevice, result);

        return new RoomCommandResponse(
                queued.id,
                roomId,
                targetDevice.getId(),
                queued.deviceId,
                queued.deviceId,
                targetDevice.getDeviceType().name(),
                queued.target,
                queued.action,
                queued.positionPercent,
                result.status(),
                queued.ts,
                "THINGSBOARD_RPC",
                result.deviceResponse()
        );
    }

    private void syncManualDesiredAngle(WindowDevice targetDevice, MappedRoomCommandRpc mapped) {
        if (!"setAngle".equals(mapped.method()) || !(mapped.params() instanceof Number angle)) {
            return;
        }
        provisioningService.syncDeviceSharedAttributes(
                targetDevice.getTbDeviceId(),
                Map.of("desiredAngle", (int) Math.round(angle.doubleValue()))
        );
    }

    private void publishTelemetryFromRpcResponse(WindowDevice targetDevice, ThingsBoardRpcResult result) {
        if (!"EXECUTED".equals(result.status())) {
            return;
        }

        Map<String, Object> telemetry = telemetryPayload(result.deviceResponse());
        if (!telemetry.isEmpty()) {
            telemetryPublisher.publish(targetDevice, telemetry);
        }
    }

    private Map<String, Object> telemetryPayload(Map<String, Object> deviceResponse) {
        Map<String, Object> telemetry = new LinkedHashMap<>();
        copyTelemetryValue(telemetry, deviceResponse, "windowOpenPercent");
        copyTelemetryValue(telemetry, deviceResponse, "blindClosedPercent");
        return telemetry;
    }

    private void copyTelemetryValue(Map<String, Object> telemetry, Map<String, Object> deviceResponse, String key) {
        if (deviceResponse == null) {
            return;
        }
        Object value = deviceResponse.get(key);
        if (value != null) {
            telemetry.put(key, value);
        }
    }

    private void recordRpcEvent(UUID roomId, WindowDevice targetDevice, RuntimeState.Command command, ThingsBoardRpcResult result) {
        runtimeStateRepository.withState(state -> {
            eventLogService.addEvent(
                    state,
                    eventLevel(result.status()),
                    command.source,
                    "ThingsBoard RPC: " + command.target + "/" + command.action,
                    "Status " + result.status() + " za uredjaj " + command.deviceId + ".",
                    roomId == null ? null : roomId.toString(),
                    targetDevice.getRoom() == null ? null : targetDevice.getRoom().getName(),
                    targetDevice.getId() == null ? null : targetDevice.getId().toString(),
                    targetDevice.getName(),
                    "Rucna komanda poslana kroz ThingsBoard RPC"
            );
            state.updatedAt = RuntimeState.now();
            return null;
        });
    }

    private String eventLevel(String status) {
        return switch (status) {
            case "EXECUTED", "SENT" -> "success";
            case "TIMEOUT", "FAILED" -> "warning";
            default -> "info";
        };
    }
}
