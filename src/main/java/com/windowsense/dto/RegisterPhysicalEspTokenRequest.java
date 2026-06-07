package com.windowsense.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RegisterPhysicalEspTokenRequest(
        @NotBlank(message = "Naziv uredjaja je obavezan.")
        String deviceName,
        @NotBlank(message = "Serijski broj ESP uredjaja je obavezan.")
        String serialNumber,
        String hardwareId,
        String firmwareVersion,
        List<String> capabilities,
        @NotBlank(message = "Kod za povezivanje je obavezan.")
        String pairingCode,
        @NotBlank(message = "ThingsBoard access token je obavezan.")
        String thingsBoardAccessToken
) {
}
