package com.windowsense.repository;

import com.windowsense.entity.WindowDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WindowDeviceRepository extends JpaRepository<WindowDevice, UUID> {

    Optional<WindowDevice> findByTbDeviceId(String tbDeviceId);

    Optional<WindowDevice> findByPhysicalHardwareId(String physicalHardwareId);

    @Query("""
            select device
            from WindowDevice device
            join fetch device.room room
            join fetch room.home home
            where device.deviceType = com.windowsense.entity.DeviceType.VIRTUAL
              and device.status = com.windowsense.entity.DeviceStatus.ACTIVE
              and device.virtual = true
            """)
    List<WindowDevice> findActiveVirtualDevicesWithRoom();

    @Query("""
            select device
            from WindowDevice device
            join fetch device.room room
            join fetch room.home home
            where device.deviceType = com.windowsense.entity.DeviceType.VIRTUAL
              and device.status = com.windowsense.entity.DeviceStatus.ACTIVE
              and device.virtual = true
              and device.tbDeviceTokenEncrypted is not null
              and device.tbDeviceTokenEncrypted <> ''
            """)
    List<WindowDevice> findActiveVirtualDevicesWithRoomAndToken();

    @Query("""
            select device
            from WindowDevice device
            join fetch device.room room
            join fetch room.home home
            where device.deviceType = com.windowsense.entity.DeviceType.PHYSICAL
              and device.status = com.windowsense.entity.DeviceStatus.ACTIVE
              and device.virtual = false
              and device.tbDeviceTokenEncrypted is not null
              and device.tbDeviceTokenEncrypted <> ''
            """)
    List<WindowDevice> findActivePhysicalDevicesWithRoomAndToken();
}
