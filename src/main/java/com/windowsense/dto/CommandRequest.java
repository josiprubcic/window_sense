package com.windowsense.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Ručna komanda za prozor, rolete ili način rada automatizacije.")
public record CommandRequest(
        @Schema(
                description = "Cilj komande. `window` upravlja prozorom, `blinds` roletama, a `automation` načinom rada.",
                example = "blinds",
                allowableValues = {"window", "blinds", "automation"}
        )
        String target,

        @Schema(
                description = "Akcija koju treba izvršiti. Za `automation` su dopuštene samo `auto` i `manual`.",
                example = "setPosition",
                allowableValues = {"open", "close", "stop", "setPosition", "auto", "manual"}
        )
        String action,

        @Schema(
                description = "Ciljana pozicija u postocima. Koristi se za `setPosition`; za rolete 0 znači gore, 100 dolje.",
                example = "85",
                minimum = "0",
                maximum = "100"
        )
        Double positionPercent,

        @Schema(
                description = "Izvor komande za event log. Ako nije poslan, backend koristi `web` ili `api`.",
                example = "swagger"
        )
        String source,

        @Schema(
                description = "Opcionalni lokalni ID uredjaja u sobi. Obavezan je samo kad vise uredjaja podrzava isti cilj komande.",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID localDeviceId
) {
        public CommandRequest(String target, String action, Double positionPercent, String source) {
                this(target, action, positionPercent, source, null);
        }
}
