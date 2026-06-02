package com.windowsense.thingsboard;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NoOpThingsBoardProvisioningService implements ThingsBoardProvisioningService {

    @Override
    public ProvisionedRoomDevice provisionVirtualRoomDevice(String roomName, String deviceName) {
        return new ProvisionedRoomDevice(
                "tb-asset-" + UUID.randomUUID(),
                "tb-device-" + UUID.randomUUID()
        );
    }
}
