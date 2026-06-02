package com.windowsense.thingsboard;

import java.util.UUID;

public record VirtualRoomDeprovisioningRequest(
        UUID roomId,
        String roomName,
        String tbAssetId,
        String tbDeviceId
) {
}
