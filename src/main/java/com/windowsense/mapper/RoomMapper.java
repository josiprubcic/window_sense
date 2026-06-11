package com.windowsense.mapper;

import com.windowsense.entity.Room;
import com.windowsense.entity.WindowDevice;
import com.windowsense.dto.RoomResponse;
import com.windowsense.dto.WindowDeviceResponse;
import org.springframework.stereotype.Component;

import com.windowsense.entity.DeviceStatus;

import java.util.stream.Collectors;

@Component
public class RoomMapper {

    public RoomResponse toResponse(Room room) {
        WindowDeviceResponse activeDevice = room.getDevices().stream()
                .filter(device -> device.getStatus() == DeviceStatus.ACTIVE)
                .findFirst()
                .map(this::toResponse)
                .orElse(null);
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getTbAssetId(),
                room.isManualMode(),
                activeDevice,
                room.getDevices().stream().map(this::toResponse).toList()
        );
    }

    private WindowDeviceResponse toResponse(WindowDevice device) {
        return new WindowDeviceResponse(
                device.getId(),
                device.getName(),
                device.getDeviceType().name(),
                device.isVirtual(),
                device.getStatus().name(),
                device.getTbDeviceId(),
                device.getPhysicalHardwareId(),
                device.getPhysicalHardwareId(),
                device.getDesiredAngleDay(),
                device.getDesiredAngleNight(),
                device.getDesiredAngleRain(),
                device.getCapabilities().stream()
                        .map(Enum::name)
                        .collect(Collectors.toSet())
        );
    }
}
