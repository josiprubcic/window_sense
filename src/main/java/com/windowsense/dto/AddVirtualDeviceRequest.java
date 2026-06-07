package com.windowsense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddVirtualDeviceRequest(
        @NotBlank(message = "Naziv uredjaja je obavezan.")
        @Size(max = 120, message = "Naziv uredjaja moze imati najvise 120 znakova.")
        String name,

        List<String> capabilities
) {
    public AddVirtualDeviceRequest(String name) {
        this(name, null);
    }
}
