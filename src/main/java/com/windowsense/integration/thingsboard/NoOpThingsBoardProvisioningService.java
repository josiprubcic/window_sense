package com.windowsense.integration.thingsboard;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "windowsense.things-board", name = "provisioning-enabled", havingValue = "false", matchIfMissing = true)
public class NoOpThingsBoardProvisioningService implements ThingsBoardProvisioningService {

    @Override
    public ProvisionedRoomAsset provisionRoomAsset(RoomAssetProvisioningRequest request) {
        return new ProvisionedRoomAsset("tb-asset-" + UUID.randomUUID());
    }

    @Override
    public ProvisionedRoomDevice provisionVirtualRoomDevice(VirtualRoomProvisioningRequest request) {
        return new ProvisionedRoomDevice(
                request.tbAssetId() == null || request.tbAssetId().isBlank() ? "tb-asset-" + UUID.randomUUID() : request.tbAssetId(),
                "tb-device-" + UUID.randomUUID(),
                null
        );
    }
}
