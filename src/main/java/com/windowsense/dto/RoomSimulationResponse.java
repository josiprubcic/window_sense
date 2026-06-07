package com.windowsense.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RoomSimulationResponse(
        UUID roomId,
        String roomName,
        UUID localDeviceId,
        String deviceId,
        String deviceType,
        String mode,
        Map<String, Object> telemetry,
        List<Decision> decisions,
        Instant updatedAt
) {
}
