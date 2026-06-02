package com.windowsense.thingsboard;

public interface ThingsBoardProvisioningService {

    ProvisionedRoomDevice provisionVirtualRoomDevice(String roomName, String deviceName);
}
