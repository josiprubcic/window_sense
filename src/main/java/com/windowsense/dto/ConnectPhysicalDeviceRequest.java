package com.windowsense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ConnectPhysicalDeviceRequest(
        @NotBlank(message = "Naziv uredjaja je obavezan.")
        @Size(max = 120, message = "Naziv uredjaja smije imati najvise 120 znakova.")
        String name,

        @NotBlank(message = "ThingsBoard Device ID je obavezan.")
        @Size(max = 128, message = "ThingsBoard Device ID smije imati najvise 128 znakova.")
        String tbDeviceId,

        @Size(max = 255, message = "ThingsBoard naziv uredjaja smije imati najvise 255 znakova.")
        String tbDeviceName,

        List<String> capabilities
) {
    public ConnectPhysicalDeviceRequest(String name, String tbDeviceId, String tbDeviceName) {
        this(name, tbDeviceId, tbDeviceName, null);
    }
}
