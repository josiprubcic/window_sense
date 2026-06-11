package com.windowsense.mapper;

import com.windowsense.entity.RuntimeState;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RoomCommandRpcMapper {

    public MappedRoomCommandRpc toRpc(RuntimeState.Command command) {
        return switch (command.target) {
            case "window" -> windowRpc(command);
            case "blinds" -> blindsRpc(command);
            default -> throw new IllegalArgumentException("NO_DEVICE_FOR_CAPABILITY");
        };
    }

    private MappedRoomCommandRpc windowRpc(RuntimeState.Command command) {
        return switch (command.action) {
            case "open" -> setAngle(90);
            case "close" -> setAngle(0);
            case "setPosition" -> setAngle(openPercentToAngle(requiredPosition(command)));
            case "stop" -> new MappedRoomCommandRpc("stopWindow", Map.of());
            default -> throw new IllegalArgumentException("Nepoznata akcija komande.");
        };
    }

    private MappedRoomCommandRpc blindsRpc(RuntimeState.Command command) {
        return switch (command.action) {
            case "open" -> setAngle(0);
            case "close" -> setAngle(90);
            case "setPosition" -> setAngle(blindClosedPercentToAngle(requiredPosition(command)));
            case "stop" -> new MappedRoomCommandRpc("stopBlinds", Map.of());
            default -> throw new IllegalArgumentException("Nepoznata akcija komande.");
        };
    }

    private MappedRoomCommandRpc setAngle(double angle) {
        return new MappedRoomCommandRpc("setAngle", angle);
    }

    private double openPercentToAngle(double openPercent) {
        return openPercent / 100.0 * 90.0;
    }

    private double blindClosedPercentToAngle(double closedPercent) {
        return closedPercent / 100.0 * 90.0;
    }

    private Double requiredPosition(RuntimeState.Command command) {
        if (command.positionPercent == null) {
            throw new IllegalArgumentException("Pozicija je obavezna za RPC komandu.");
        }
        return command.positionPercent;
    }
}
