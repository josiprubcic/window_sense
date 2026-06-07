package com.windowsense.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterPhysicalEspTokenOnlyRequest(
        @NotBlank(message = "ThingsBoard access token je obavezan.")
        String thingsBoardAccessToken
) {
}
