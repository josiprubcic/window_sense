package com.windowsense.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WindowDeviceRepository extends JpaRepository<WindowDevice, UUID> {
}
