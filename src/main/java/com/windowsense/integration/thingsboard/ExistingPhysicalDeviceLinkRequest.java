package com.windowsense.integration.thingsboard;

import java.util.UUID;

public record ExistingPhysicalDeviceLinkRequest(
        UUID roomId,
        String roomName,
        String tbAssetId,
        String tbDeviceId,
        String deviceName,
        String tbDeviceName,
        UUID appUserId,
        String auth0Sub
) {
}
