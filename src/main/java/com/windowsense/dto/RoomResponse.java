package com.windowsense.dto;

import java.util.List;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String name,
        String tbAssetId,
        boolean manualMode,
        WindowDeviceResponse activeDevice,
        List<WindowDeviceResponse> devices
) {
}
