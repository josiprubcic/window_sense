package com.windowsense.thingsboard;

import java.util.UUID;

public record VirtualRoomProvisioningRequest(
        UUID roomId,
        String roomName,
        String deviceName,
        UUID appUserId,
        String auth0Sub
) {
}
