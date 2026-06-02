package com.windowsense.room.dto;

import com.windowsense.device.DeviceStatus;
import com.windowsense.device.DeviceType;

import java.util.UUID;

public record WindowDeviceResponse(
        UUID id,
        String name,
        DeviceType deviceType,
        boolean isVirtual,
        DeviceStatus status,
        String tbDeviceId
) {
}
