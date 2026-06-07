package com.windowsense.service;

import com.windowsense.dto.CommandRequest;
import com.windowsense.service.CommandResult;
import com.windowsense.entity.RuntimeState;
import com.windowsense.entity.WindowDevice;
import com.windowsense.dto.RoomCommandResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "windowsense.commands", name = "physical-delivery", havingValue = "polling", matchIfMissing = true)
public class PollingCommandDeliveryPort implements CommandDeliveryPort {

    private final CommandService commandService;

    public PollingCommandDeliveryPort(CommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public RoomCommandResponse deliver(UUID roomId, WindowDevice targetDevice, CommandRequest command) {
        CommandResult result = commandService.enqueueDeviceCommand(targetDevice.getTbDeviceId(), command);
        RuntimeState.Command queued = result.queued;
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
                "QUEUED",
                queued.ts,
                "POLLING",
                Map.of()
        );
    }
}
