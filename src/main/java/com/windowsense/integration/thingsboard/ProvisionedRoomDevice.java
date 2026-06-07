package com.windowsense.integration.thingsboard;

public record ProvisionedRoomDevice(
        String tbAssetId,
        String tbDeviceId,
        String tbDeviceAccessToken
) {
}
