package com.windowsense.controller;

import com.windowsense.dto.AckRequest;
import com.windowsense.dto.DeviceBootstrapRequest;
import com.windowsense.dto.DeviceBootstrapResponse;
import com.windowsense.service.CommandService;
import com.windowsense.service.DeviceBootstrapService;
import com.windowsense.service.RuntimeStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@Tag(
        name = "WindowSense",
        description = "API za health, event log i ESP32 device polling/bootstrap."
)
public class WindowSenseController {

    private final RuntimeStateService runtimeStateService;
    private final CommandService commandService;
    private final DeviceBootstrapService deviceBootstrapService;

    public WindowSenseController(
            RuntimeStateService runtimeStateService,
            CommandService commandService,
            DeviceBootstrapService deviceBootstrapService
    ) {
        this.runtimeStateService = runtimeStateService;
        this.commandService = commandService;
        this.deviceBootstrapService = deviceBootstrapService;
    }

    @GetMapping("/api/health")
    @Operation(
            summary = "Provjera dostupnosti API-ja",
            description = "Vraća osnovne informacije o backendu i trenutno serversko vrijeme."
    )
    public Map<String, Object> health() {
        return Map.of(
                "ok", true,
                "service", "WindowSense API",
                "time", runtimeStateService.currentTime()
        );
    }

    @GetMapping("/api/events")
    @Operation(
            summary = "Dohvati event log",
            description = "Vraća zadnje događaje sustava, npr. zaprimljenu telemetriju, automatske odluke i potvrde uređaja."
    )
    public Map<String, Object> events() {
        return Map.of("events", runtimeStateService.events());
    }

    @GetMapping("/api/esp/{serialNumber}/commands")
    @Operation(
            summary = "Dohvati pending komande za ESP po serijskom broju",
            description = """
                    Room-first ESP endpoint. ESP šalje vlastiti serijski broj, a backend pronalazi lokalni
                    fizički uređaj povezan sa sobom i vraća samo njegove pending komande.
                    """
    )
    public Map<String, Object> espCommands(
            @Parameter(description = "Serijski broj fizičkog ESP uređaja.", example = "WS-ESP32-0001")
            @PathVariable String serialNumber
    ) {
        return Map.of("commands", commandService.pollCommandsForSerialNumber(serialNumber));
    }

    @PostMapping("/api/device/bootstrap")
    @Operation(
            summary = "Bootstrap konfiguracija za fizički ESP32",
            description = """
                    Endpoint koji ESP32 poziva nakon provisioning flowa. Uredjaj mora poslati serijski broj,
                    skriveni device secret i kratkotrajni provisioning session ID. Ako su vrijednosti ispravne,
                    backend vraća ThingsBoard MQTT access token direktno ESP-u. Token se ne vraća frontend dashboardu.
                    """
    )
    public ResponseEntity<DeviceBootstrapResponse> bootstrapDevice(@Valid @RequestBody DeviceBootstrapRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(deviceBootstrapService.bootstrap(request));
    }

    @PostMapping("/api/esp/{serialNumber}/ack")
    @Operation(
            summary = "Potvrdi ESP komandu po serijskom broju",
            description = """
                    Room-first ESP endpoint. Backend mapira serijski broj na fizički uređaj u sobi i potvrđuje
                    samo komandu koja pripada tom uređaju.
                    """
    )
    public ResponseEntity<Object> acknowledgeEspCommand(
            @Parameter(description = "Serijski broj fizičkog ESP uređaja.", example = "WS-ESP32-0001")
            @PathVariable String serialNumber,
            @RequestBody AckRequest request
    ) {
        // TODO: require an X-WindowSense-Device-Token or equivalent device authentication before production use.
        Object command = commandService.acknowledgeCommandForSerialNumber(
                request.commandId(),
                serialNumber,
                request.status()
        );
        if (command == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Komanda nije pronadjena."));
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(command);
    }

}
