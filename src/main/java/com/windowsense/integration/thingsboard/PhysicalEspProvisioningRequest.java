package com.windowsense.integration.thingsboard;

import java.util.List;
import java.util.UUID;

public record PhysicalEspProvisioningRequest(
        UUID roomId,
        String roomName,
        String tbAssetId,
        String deviceName,
        String serialNumber,
        String hardwareId,
        String firmwareVersion,
        List<String> capabilities,
        UUID appUserId,
        String auth0Sub
) {
}
