package com.windowsense.room;

import com.windowsense.device.WindowDevice;
import com.windowsense.room.dto.RoomResponse;
import com.windowsense.room.dto.WindowDeviceResponse;
import org.springframework.stereotype.Component;

@Component
public class RoomMapper {

    public RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getTbAssetId(),
                room.getDevices().stream().map(this::toResponse).toList()
        );
    }

    private WindowDeviceResponse toResponse(WindowDevice device) {
        return new WindowDeviceResponse(
                device.getId(),
                device.getName(),
                device.getDeviceType(),
                device.isVirtual(),
                device.getStatus(),
                device.getTbDeviceId()
        );
    }
}
