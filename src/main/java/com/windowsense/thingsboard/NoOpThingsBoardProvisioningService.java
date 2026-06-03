package com.windowsense.thingsboard;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "windowsense.things-board", name = "provisioning-enabled", havingValue = "false", matchIfMissing = true)
public class NoOpThingsBoardProvisioningService implements ThingsBoardProvisioningService {

    @Override
    public ProvisionedRoomDevice provisionVirtualRoomDevice(VirtualRoomProvisioningRequest request) {
        return new ProvisionedRoomDevice(
                "tb-asset-" + UUID.randomUUID(),
                "tb-device-" + UUID.randomUUID(),
                null
        );
    }
}
