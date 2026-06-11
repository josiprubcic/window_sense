package com.windowsense.integration.thingsboard;

import java.util.List;
import java.util.UUID;

public record ExistingPhysicalDeviceLinkRequest(
        UUID roomId,
        String roomName,
        String tbAssetId,
        String tbDeviceId,
        String deviceName,
        String tbDeviceName,
        List<String> capabilities,
        UUID appUserId,
        String auth0Sub
) {
    public ExistingPhysicalDeviceLinkRequest(
            UUID roomId,
            String roomName,
            String tbAssetId,
            String tbDeviceId,
            String deviceName,
            String tbDeviceName,
            UUID appUserId,
            String auth0Sub
    ) {
        this(roomId, roomName, tbAssetId, tbDeviceId, deviceName, tbDeviceName, List.of(), appUserId, auth0Sub);
    }
}
