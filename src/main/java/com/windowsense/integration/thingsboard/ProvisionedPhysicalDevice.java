package com.windowsense.integration.thingsboard;

public record ProvisionedPhysicalDevice(
        String tbDeviceId,
        String tbDeviceAccessToken
) {
}
