package com.windowsense.integration.thingsboard;

public interface ThingsBoardProvisioningService {

    default ProvisionedRoomAsset provisionRoomAsset(RoomAssetProvisioningRequest request) {
        return new ProvisionedRoomAsset("tb-asset-" + request.roomId());
    }

    ProvisionedRoomDevice provisionVirtualRoomDevice(VirtualRoomProvisioningRequest request);

    default ProvisionedPhysicalDevice provisionPhysicalEspDevice(PhysicalEspProvisioningRequest request) {
        return new ProvisionedPhysicalDevice(
                "tb-device-" + request.roomId() + "-" + request.serialNumber(),
                null
        );
    }

    default RegisteredPhysicalEspDevice registerPhysicalEspDeviceWithToken(PhysicalEspTokenRegistrationRequest request) {
        return new RegisteredPhysicalEspDevice("tb-device-" + request.serialNumber());
    }

    default void linkExistingPhysicalDevice(ExistingPhysicalDeviceLinkRequest request) {
    }

    default void deprovisionVirtualRoom(VirtualRoomDeprovisioningRequest request) {
    }

    default void deprovisionRoomDevice(RoomDeviceDeprovisioningRequest request) {
    }

    default void deprovisionRoomAsset(RoomAssetDeprovisioningRequest request) {
    }
}
