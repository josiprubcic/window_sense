package com.windowsense.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRoomRequest(
        @NotBlank(message = "Naziv sobe je obavezan.")
        @Size(max = 120, message = "Naziv sobe smije imati najvise 120 znakova.")
        String name
) {
}
