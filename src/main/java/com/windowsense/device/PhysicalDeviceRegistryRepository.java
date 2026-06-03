package com.windowsense.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PhysicalDeviceRegistryRepository extends JpaRepository<PhysicalDeviceRegistry, UUID> {

    Optional<PhysicalDeviceRegistry> findByPairingCodeHash(String pairingCodeHash);
}
