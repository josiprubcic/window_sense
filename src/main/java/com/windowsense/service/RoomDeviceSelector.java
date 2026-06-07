package com.windowsense.service;

import com.windowsense.exception.ConflictException;
import com.windowsense.exception.ResourceNotFoundException;
import com.windowsense.entity.DeviceCapability;
import com.windowsense.entity.DeviceStatus;
import com.windowsense.entity.DeviceType;
import com.windowsense.entity.Room;
import com.windowsense.entity.WindowDevice;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RoomDeviceSelector {

    public WindowDevice commandTarget(Room room, DeviceCapability capability, UUID localDeviceId) {
        if (localDeviceId != null) {
            WindowDevice device = activeDeviceById(room, localDeviceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Uredjaj nije pronadjen u sobi."));
            if (!device.hasCapability(capability)) {
                throw new ConflictException("DEVICE_DOES_NOT_SUPPORT_CAPABILITY");
            }
            return device;
        }

        List<WindowDevice> candidates = activeDevicesWithCapability(room, capability);
        if (candidates.isEmpty()) {
            throw new ConflictException("NO_DEVICE_FOR_CAPABILITY");
        }
        if (candidates.size() > 1) {
            throw new ConflictException("MULTIPLE_DEVICES_FOR_CAPABILITY");
        }
        return candidates.getFirst();
    }

    public Optional<WindowDevice> activeControllableDevice(Room room) {
        return activeDevice(room, DeviceType.PHYSICAL)
                .or(() -> activeDevice(room, DeviceType.VIRTUAL));
    }

    public WindowDevice activeSimulationDevice(Room room) {
        return activeDevice(room, DeviceType.VIRTUAL)
                .orElseThrow(() -> new ResourceNotFoundException("Soba nema aktivni virtualni uredjaj."));
    }

    private Optional<WindowDevice> activeDeviceById(Room room, UUID localDeviceId) {
        return room.getDevices().stream()
                .filter(device -> device.getStatus() == DeviceStatus.ACTIVE)
                .filter(device -> localDeviceId.equals(device.getId()))
                .findFirst();
    }

    private Optional<WindowDevice> activeDevice(Room room, DeviceType deviceType) {
        return room.getDevices().stream()
                .filter(device -> device.getDeviceType() == deviceType)
                .filter(device -> device.getStatus() == DeviceStatus.ACTIVE)
                .findFirst();
    }

    private List<WindowDevice> activeDevicesWithCapability(Room room, DeviceCapability capability) {
        return room.getDevices().stream()
                .filter(device -> device.getStatus() == DeviceStatus.ACTIVE)
                .filter(device -> device.hasCapability(capability))
                .toList();
    }
}
