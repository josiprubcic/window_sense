package com.windowsense.integration.thingsboard;

import java.util.UUID;

public record VirtualRoomProvisioningRequest(
        UUID roomId,
        String roomName,
        String tbAssetId,
        String deviceName,
        UUID appUserId,
        String auth0Sub
) {
    public VirtualRoomProvisioningRequest(
            UUID roomId,
            String roomName,
            String deviceName,
            UUID appUserId,
            String auth0Sub
    ) {
        this(roomId, roomName, null, deviceName, appUserId, auth0Sub);
    }
}
