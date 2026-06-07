package com.windowsense.integration.thingsboard;

import java.util.UUID;

public record RoomDeviceDeprovisioningRequest(
        UUID roomId,
        String roomName,
        String tbAssetId,
        String tbDeviceId
) {
}
