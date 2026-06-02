package com.windowsense.room.dto;

import java.util.List;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String name,
        String tbAssetId,
        List<WindowDeviceResponse> devices
) {
}
