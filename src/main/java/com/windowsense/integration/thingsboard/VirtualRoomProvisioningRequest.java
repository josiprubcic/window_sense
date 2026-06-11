package com.windowsense.integration.thingsboard;

import java.util.List;
import java.util.UUID;

public record VirtualRoomProvisioningRequest(
        UUID roomId,
        String roomName,
        String tbAssetId,
        String deviceName,
        List<String> capabilities,
        UUID appUserId,
        String auth0Sub
) {
    public VirtualRoomProvisioningRequest(
            UUID roomId,
            String roomName,
            String tbAssetId,
            String deviceName,
            UUID appUserId,
            String auth0Sub
    ) {
        this(roomId, roomName, tbAssetId, deviceName, List.of(), appUserId, auth0Sub);
    }

    public VirtualRoomProvisioningRequest(
            UUID roomId,
            String roomName,
            String deviceName,
            UUID appUserId,
            String auth0Sub
    ) {
        this(roomId, roomName, null, deviceName, List.of(), appUserId, auth0Sub);
    }
}
