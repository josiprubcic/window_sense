package com.windowsense.service;

import com.windowsense.exception.ConflictException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.entity.DeviceType;
import com.windowsense.entity.WindowDevice;
import com.windowsense.dto.CommandRequest;
import com.windowsense.service.CommandResult;
import com.windowsense.entity.RuntimeState;
import com.windowsense.repository.RuntimeStateRepository;
import com.windowsense.dto.RoomCommandResponse;
import com.windowsense.service.CommandService;
import com.windowsense.service.EventLogService;
import com.windowsense.mapper.MappedRoomCommandRpc;
import com.windowsense.mapper.RoomCommandRpcMapper;
import com.windowsense.integration.thingsboard.ThingsBoardRpcRequest;
import com.windowsense.integration.thingsboard.ThingsBoardRpcResult;
import com.windowsense.integration.thingsboard.ThingsBoardRpcService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class VirtualThingsBoardRpcCommandDeliveryPort {

    private final CommandService commandService;
    private final RoomCommandRpcMapper rpcMapper;
    private final ThingsBoardRpcService rpcService;
    private final WindowSenseProperties properties;
    private final RuntimeStateRepository runtimeStateRepository;
    private final EventLogService eventLogService;

    public VirtualThingsBoardRpcCommandDeliveryPort(
            CommandService commandService,
            RoomCommandRpcMapper rpcMapper,
            ThingsBoardRpcService rpcService,
            WindowSenseProperties properties,
            RuntimeStateRepository runtimeStateRepository,
            EventLogService eventLogService
    ) {
        this.commandService = commandService;
        this.rpcMapper = rpcMapper;
        this.rpcService = rpcService;
        this.properties = properties;
        this.runtimeStateRepository = runtimeStateRepository;
        this.eventLogService = eventLogService;
    }

    public boolean isReady() {
        return properties.getVirtualSimulator().isMqttRpcEnabled()
                && properties.getCommands().getRpc().isEnabled()
                && properties.getThingsBoard().isRestAuthReady();
    }

    public RoomCommandResponse deliver(UUID roomId, WindowDevice targetDevice, CommandRequest command) {
        if (targetDevice.getDeviceType() != DeviceType.VIRTUAL) {
            throw new IllegalArgumentException("Virtualni ThingsBoard RPC podrzava samo virtualne uredjaje.");
        }
        if (!isReady()) {
            throw new ConflictException("VIRTUAL_THINGSBOARD_RPC_NOT_CONFIGURED");
        }
        if (targetDevice.getTbDeviceId() == null || targetDevice.getTbDeviceId().isBlank()) {
            throw new ConflictException("THINGSBOARD_DEVICE_ID_REQUIRED");
        }

        CommandResult prepared = commandService.prepareDeviceCommand(targetDevice.getTbDeviceId(), command);
        RuntimeState.Command queued = prepared.queued;
        MappedRoomCommandRpc mapped = rpcMapper.toRpc(queued);
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

    private void recordRpcEvent(UUID roomId, WindowDevice targetDevice, RuntimeState.Command command, ThingsBoardRpcResult result) {
        runtimeStateRepository.withState(state -> {
            eventLogService.addEvent(
                    state,
                    eventLevel(result.status()),
                    command.source,
                    "ThingsBoard RPC: " + command.target + "/" + command.action,
                    "Status " + result.status() + " za virtualni uredjaj " + command.deviceId + ".",
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
