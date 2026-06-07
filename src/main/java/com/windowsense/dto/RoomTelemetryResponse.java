package com.windowsense.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RoomTelemetryResponse(
        UUID roomId,
        String roomName,
        UUID deviceId,
        String deviceName,
        String deviceType,
        boolean isVirtual,
        Map<String, Object> telemetry,
        Instant updatedAt,
        String status,
        String code,
        String message,
        List<RoomDeviceTelemetryResponse> devices,
        Map<String, Object> aggregated
) {
    public RoomTelemetryResponse(
            UUID roomId,
            String roomName,
            UUID deviceId,
            String deviceName,
            String deviceType,
            boolean isVirtual,
            Map<String, Object> telemetry,
            Instant updatedAt,
            String status,
            String code,
            String message
    ) {
        this(roomId, roomName, deviceId, deviceName, deviceType, isVirtual, telemetry, updatedAt, status, code, message, List.of(), telemetry);
    }
}
