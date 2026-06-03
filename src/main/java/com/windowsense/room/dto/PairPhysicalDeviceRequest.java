package com.windowsense.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PairPhysicalDeviceRequest(
        @NotBlank(message = "Naziv uredjaja je obavezan.")
        @Size(max = 120, message = "Naziv uredjaja smije imati najvise 120 znakova.")
        String name,

        @NotBlank(message = "Kod za povezivanje je obavezan.")
        @Size(max = 64, message = "Kod za povezivanje smije imati najvise 64 znaka.")
        String pairingCode
) {
}
