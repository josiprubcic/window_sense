package com.windowsense.dto;

import java.util.Set;
import java.util.UUID;

public record WindowDeviceResponse(
        UUID id,
        String name,
        String deviceType,
        boolean isVirtual,
        String status,
        String tbDeviceId,
        String serialNumber,
        String deviceUid,
        Set<String> capabilities
) {
}
