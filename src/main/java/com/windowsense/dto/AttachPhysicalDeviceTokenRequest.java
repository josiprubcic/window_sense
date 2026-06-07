package com.windowsense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AttachPhysicalDeviceTokenRequest(
        @NotBlank(message = "Naziv uredjaja je obavezan.")
        @Size(max = 120, message = "Naziv uredjaja smije imati najvise 120 znakova.")
        String name,

        @NotBlank(message = "ESP ThingsBoard token je obavezan.")
        String thingsBoardAccessToken
) {
}
