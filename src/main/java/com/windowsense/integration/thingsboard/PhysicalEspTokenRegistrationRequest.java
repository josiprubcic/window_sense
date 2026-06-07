package com.windowsense.integration.thingsboard;

import java.util.List;

public record PhysicalEspTokenRegistrationRequest(
        String deviceName,
        String serialNumber,
        String hardwareId,
        String firmwareVersion,
        List<String> capabilities,
        String thingsBoardAccessToken
) {
}
