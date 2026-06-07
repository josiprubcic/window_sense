package com.windowsense.integration.thingsboard;

import java.util.UUID;

public record RoomAssetProvisioningRequest(
        UUID roomId,
        String roomName,
        UUID appUserId,
        String auth0Sub
) {
}
