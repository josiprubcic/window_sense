package com.windowsense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PairPhysicalDeviceRequest(
        @Size(max = 120, message = "Naziv uredjaja smije imati najvise 120 znakova.")
        String name,

        @NotBlank(message = "Serijski broj uredjaja je obavezan.")
        @Size(max = 128, message = "Serijski broj uredjaja smije imati najvise 128 znakova.")
        String serialNumber,

        @NotBlank(message = "Kod za povezivanje je obavezan.")
        @Size(max = 64, message = "Kod za povezivanje smije imati najvise 64 znaka.")
        String pairingCode,

        List<String> capabilities
) {
    public PairPhysicalDeviceRequest(String name, String pairingCode) {
        this(name, null, pairingCode, null);
    }

    public PairPhysicalDeviceRequest(String name, String serialNumber, String pairingCode) {
        this(name, serialNumber, pairingCode, null);
    }
}
