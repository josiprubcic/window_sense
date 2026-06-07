package com.windowsense.dto;

import java.time.Instant;
import java.util.UUID;

public record ProvisionPhysicalEspResponse(
        UUID roomId,
        String roomName,
        UUID localDeviceId,
        String tbDeviceId,
        String serialNumber,
        String hardwareId,
        String status,
        String provisioningSessionId,
        Instant provisioningSessionExpiresAt
) {
}
