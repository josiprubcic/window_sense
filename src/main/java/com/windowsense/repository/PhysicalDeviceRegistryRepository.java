package com.windowsense.repository;

import com.windowsense.entity.PhysicalDeviceRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PhysicalDeviceRegistryRepository extends JpaRepository<PhysicalDeviceRegistry, UUID> {

    Optional<PhysicalDeviceRegistry> findBySerialNumber(String serialNumber);

    Optional<PhysicalDeviceRegistry> findByThingsBoardAccessTokenHash(String thingsBoardAccessTokenHash);

    boolean existsBySerialNumber(String serialNumber);

    boolean existsByHardwareId(String hardwareId);

    boolean existsByThingsBoardAccessTokenHash(String thingsBoardAccessTokenHash);
}
