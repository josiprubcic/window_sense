package com.windowsense.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Potvrda uređaja da je komanda iz queuea obrađena.")
public record AckRequest(
        @Schema(description = "ID komande dobiven preko ESP ili device command polling endpointa.", example = "cmd-example")
        String commandId,

        @Schema(
                description = "Status izvršenja. Ako nije poslan, backend koristi `acknowledged`.",
                example = "acknowledged"
        )
        String status
) {
}
