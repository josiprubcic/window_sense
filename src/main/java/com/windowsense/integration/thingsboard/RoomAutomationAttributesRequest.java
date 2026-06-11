package com.windowsense.integration.thingsboard;

import java.util.UUID;

public record RoomAutomationAttributesRequest(
        UUID roomId,
        String roomName,
        String tbDeviceId,
        double rainThreshold,
        boolean manualMode
) {
}
