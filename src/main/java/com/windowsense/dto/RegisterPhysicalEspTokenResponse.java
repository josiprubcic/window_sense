package com.windowsense.dto;

public record RegisterPhysicalEspTokenResponse(
        String serialNumber,
        String hardwareId,
        String tbDeviceId,
        String status
) {
}
