package com.windowsense.thingsboard;

public interface ThingsBoardProvisioningService {

    ProvisionedRoomDevice provisionVirtualRoomDevice(VirtualRoomProvisioningRequest request);

    default void markRoomDeviceDeleted(String tbAssetId, String tbDeviceId) {
    }
}
