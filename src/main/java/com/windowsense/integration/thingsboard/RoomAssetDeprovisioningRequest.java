package com.windowsense.integration.thingsboard;

import java.util.UUID;

public record RoomAssetDeprovisioningRequest(
        UUID roomId,
        String roomName,
        String tbAssetId
) {
}
