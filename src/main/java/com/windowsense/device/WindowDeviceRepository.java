package com.windowsense.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WindowDeviceRepository extends JpaRepository<WindowDevice, UUID> {

    boolean existsByRoomIdAndDeviceTypeAndStatus(UUID roomId, DeviceType deviceType, DeviceStatus status);

    @Query("""
            select device
            from WindowDevice device
            join fetch device.room room
            where device.deviceType = com.windowsense.device.DeviceType.VIRTUAL
              and device.status = com.windowsense.device.DeviceStatus.ACTIVE
              and device.virtual = true
            """)
    List<WindowDevice> findActiveVirtualDevicesWithRoom();
}
