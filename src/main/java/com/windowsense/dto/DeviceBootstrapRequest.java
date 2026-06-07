package com.windowsense.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceBootstrapRequest(
        @NotBlank(message = "Serijski broj uredjaja je obavezan.")
        String serialNumber,
        @NotBlank(message = "Tajni kljuc uredjaja je obavezan.")
        String deviceSecret,
        @NotBlank(message = "Provisioning session ID je obavezan.")
        String provisioningSessionId
) {
}
