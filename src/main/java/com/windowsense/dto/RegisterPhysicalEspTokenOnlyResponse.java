package com.windowsense.dto;

public record RegisterPhysicalEspTokenOnlyResponse(
        String serialNumber,
        String deviceName,
        String pairingCode,
        String tbDeviceId,
        String status
) {
}
