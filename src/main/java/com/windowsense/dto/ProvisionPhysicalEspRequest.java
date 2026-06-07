package com.windowsense.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ProvisionPhysicalEspRequest(
        @NotBlank(message = "Naziv uredjaja je obavezan.")
        String name,
        @NotBlank(message = "Kod za povezivanje je obavezan.")
        String pairingCode,
        @NotBlank(message = "Hash koda za povezivanje s ESP-a je obavezan.")
        String pairingCodeHash,
        @NotBlank(message = "Serijski broj ESP uredjaja je obavezan.")
        String serialNumber,
        String hardwareId,
        String firmwareVersion,
        List<String> capabilities,
        @NotBlank(message = "Hash tajnog kljuca uredjaja je obavezan.")
        String deviceSecretHash
) {
}
