package com.windowsense.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record RoomDeviceTelemetryResponse(
        UUID deviceId,
        String deviceName,
        String serialNumber,
        String deviceType,
        boolean isVirtual,
        String status,
        Set<String> capabilities,
        Map<String, Object> telemetry,
        Instant updatedAt
) {
}
