package com.windowsense.thingsboard;

public record ProvisionedRoomDevice(
        String tbAssetId,
        String tbDeviceId,
        String tbDeviceAccessToken
) {
}
