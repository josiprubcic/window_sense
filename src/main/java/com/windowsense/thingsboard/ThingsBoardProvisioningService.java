package com.windowsense.thingsboard;

public interface ThingsBoardProvisioningService {

    ProvisionedRoomDevice provisionVirtualRoomDevice(VirtualRoomProvisioningRequest request);

    default void linkExistingPhysicalDevice(ExistingPhysicalDeviceLinkRequest request) {
    }

    default void deprovisionVirtualRoom(VirtualRoomDeprovisioningRequest request) {
    }
}
