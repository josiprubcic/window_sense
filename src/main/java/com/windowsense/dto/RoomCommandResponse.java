package com.windowsense.dto;

import java.util.Map;
import java.util.UUID;

public record RoomCommandResponse(
        String commandId,
        UUID roomId,
        UUID localDeviceId,
        String deviceId,
        String tbDeviceId,
        String deviceType,
        String target,
        String action,
        Double positionPercent,
        String status,
        String createdAt,
        String delivery,
        Map<String, Object> deviceResponse
) {
    public RoomCommandResponse(
            String commandId,
            UUID roomId,
            UUID localDeviceId,
            String deviceId,
            String deviceType,
            String target,
            String action,
            Double positionPercent,
            String status,
            String createdAt
    ) {
        this(
                commandId,
                roomId,
                localDeviceId,
                deviceId,
                deviceId,
                deviceType,
                target,
                action,
                positionPercent,
                status,
                createdAt,
                null,
                Map.of()
        );
    }
}
