package com.windowsense.room.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RoomTelemetryResponse(
        UUID roomId,
        String roomName,
        UUID deviceId,
        String deviceName,
        Map<String, Object> telemetry,
        Instant updatedAt,
        String message
) {
}
