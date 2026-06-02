package com.windowsense.thingsboard;

public interface ThingsBoardProvisioningService {

    ProvisionedRoomDevice provisionVirtualRoomDevice(VirtualRoomProvisioningRequest request);

    default void deprovisionVirtualRoom(VirtualRoomDeprovisioningRequest request) {
    }
}
