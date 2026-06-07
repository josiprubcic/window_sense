package com.windowsense.mapper;

import com.windowsense.entity.RuntimeState;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RoomCommandRpcMapper {

    public MappedRoomCommandRpc toRpc(RuntimeState.Command command) {
        String method = switch (command.target) {
            case "window" -> windowMethod(command.action);
            case "blinds" -> blindsMethod(command.action);
            default -> throw new IllegalArgumentException("NO_DEVICE_FOR_CAPABILITY");
        };

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("commandId", command.id);
        if (!"stop".equals(command.action)) {
            params.put("position", requiredPosition(command));
        }
        return new MappedRoomCommandRpc(method, params);
    }

    private String windowMethod(String action) {
        return switch (action) {
            case "open" -> "openWindow";
            case "close" -> "closeWindow";
            case "setPosition" -> "setWindowPosition";
            case "stop" -> "stopWindow";
            default -> throw new IllegalArgumentException("Nepoznata akcija komande.");
        };
    }

    private String blindsMethod(String action) {
        return switch (action) {
            case "open" -> "openBlinds";
            case "close" -> "closeBlinds";
            case "setPosition" -> "setBlindsPosition";
            case "stop" -> "stopBlinds";
            default -> throw new IllegalArgumentException("Nepoznata akcija komande.");
        };
    }

    private Double requiredPosition(RuntimeState.Command command) {
        if (command.positionPercent == null) {
            throw new IllegalArgumentException("Pozicija je obavezna za RPC komandu.");
        }
        return command.positionPercent;
    }
}
