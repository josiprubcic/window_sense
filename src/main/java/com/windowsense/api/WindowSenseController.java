package com.windowsense.api;

import com.windowsense.config.WindowSenseProperties;
import com.windowsense.model.AckRequest;
import com.windowsense.model.CommandRequest;
import com.windowsense.model.TelemetryResult;
import com.windowsense.model.ThresholdUpdateResult;
import com.windowsense.model.WindowSenseState;
import com.windowsense.repository.WindowSenseStateRepository;
import com.windowsense.service.CommandService;
import com.windowsense.service.TelemetryService;
import com.windowsense.service.ThresholdService;
import com.windowsense.service.WeatherService;
import com.windowsense.stream.StateStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@RestController
@Tag(
        name = "WindowSense",
        description = "API za lokalno stanje pametnog prozora, roleta, automatizaciju, uređajske komande i ThingsBoard telemetriju."
)
public class WindowSenseController {

    private final WindowSenseStateRepository repository;
    private final TelemetryService telemetryService;
    private final WeatherService weatherService;
    private final ThresholdService thresholdService;
    private final CommandService commandService;
    private final StateStreamService stateStreamService;
    private final WindowSenseProperties properties;

    public WindowSenseController(
            WindowSenseStateRepository repository,
            TelemetryService telemetryService,
            WeatherService weatherService,
            ThresholdService thresholdService,
            CommandService commandService,
            StateStreamService stateStreamService,
            WindowSenseProperties properties
    ) {
        this.repository = repository;
        this.telemetryService = telemetryService;
        this.weatherService = weatherService;
        this.thresholdService = thresholdService;
        this.commandService = commandService;
        this.stateStreamService = stateStreamService;
        this.properties = properties;
    }

    @GetMapping("/api/health")
    @Operation(
            summary = "Provjera dostupnosti API-ja",
            description = "Vraća osnovne informacije o backendu, logički ID uređaja i trenutno serversko vrijeme."
    )
    public Map<String, Object> health() {
        return Map.of(
                "ok", true,
                "service", "WindowSense API",
                "deviceId", properties.getDeviceId(),
                "time", WindowSenseState.now()
        );
    }

    @GetMapping("/api/state")
    @Operation(
            summary = "Dohvati trenutno stanje sustava",
            description = """
                    Vraća kompletan in-memory snapshot sustava: senzore, vremenske podatke, pozicije prozora i roleta,
                    pragove automatizacije, ThingsBoard status, command queue i zadnje evente.
                    """
    )
    public WindowSenseState state() {
        return repository.getState();
    }

    @GetMapping("/api/events")
    @Operation(
            summary = "Dohvati event log",
            description = "Vraća zadnje događaje sustava, npr. zaprimljenu telemetriju, automatske odluke i potvrde uređaja."
    )
    public Map<String, Object> events() {
        return Map.of("events", repository.getState().events);
    }

    @GetMapping(path = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Live stream stanja",
            description = """
                    Otvara Server-Sent Events stream. Backend odmah šalje trenutno stanje, a zatim šalje novi `state`
                    event nakon svake promjene telemetrije, komande, pragova, vremena ili ThingsBoard statusa.
                    """
    )
    public SseEmitter stream() throws IOException {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.send(SseEmitter.event().name("state").data(repository.getState()));
        Runnable unsubscribe = stateStreamService.subscribe(state -> sendState(emitter, state));
        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(unsubscribe);
        emitter.onError(error -> unsubscribe.run());
        return emitter;
    }

    @PostMapping("/api/telemetry")
    @Operation(
            summary = "Zaprimi telemetriju senzora",
            description = """
                    Prima djelomični telemetry payload iz ESP32 uređaja, web simulatora ili ručnog testa.
                    Poslana polja ažuriraju lokalno stanje, zatim se pokreće automatizacija i objavljuje promjena
                    prema UI streamu i ThingsBoard sinkronizaciji. Polja koja nisu poslana zadržavaju staru vrijednost.
                    `source` je opcionalan i koristi se samo za event log; ako nije poslan, koristi se `device`.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Djelomični telemetry payload. Sva polja su opcionalna osim ako ih vaš uređaj želi ažurirati.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @ExampleObject(
                                            name = "Senzori i pozicije",
                                            value = """
                                                    {
                                                      "source": "esp32",
                                                      "rainDetected": false,
                                                      "rainIntensity": 0,
                                                      "lightLux": 80000,
                                                      "windowOpenPercent": 65,
                                                      "blindsPositionPercent": 10,
                                                      "indoorTempC": 24.8,
                                                      "outdoorTempC": 20.9,
                                                      "batteryPercent": 94,
                                                      "signalStrength": -58
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Simulator zasjene",
                                            value = """
                                                    {
                                                      "source": "web-simulator",
                                                      "lightLux": 80000,
                                                      "blindsPositionPercent": 10
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    )
    public ResponseEntity<TelemetryResult> telemetry(@RequestBody Map<String, Object> payload) {
        String source = payload.get("source") instanceof String text && !text.isBlank() ? text : "device";
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(telemetryService.ingestTelemetry(payload, source));
    }

    @PostMapping("/api/weather")
    @Operation(
            summary = "Ažuriraj vremenske podatke",
            description = """
                    Ažurira vremenski dio stanja: opis vremena, vjerojatnost kiše, brzinu vjetra i izvor prognoze.
                    Nakon promjene se ponovno pokreće automatizacija, pa npr. visoka vjerojatnost kiše ili jak vjetar
                    mogu generirati komandu za zatvaranje prozora.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    name = "Prognoza",
                                    value = """
                                            {
                                              "condition": "Kisa",
                                              "rainProbability": 75,
                                              "windKph": 18,
                                              "forecastSource": "manual"
                                            }
                                            """
                            )
                    )
            )
    )
    public ResponseEntity<TelemetryResult> weather(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(weatherService.updateWeather(payload));
    }

    @PostMapping("/api/automation/thresholds")
    @Operation(
            summary = "Ažuriraj pragove automatizacije",
            description = """
                    Mijenja pragove po kojima automatizacija donosi odluke. Primjerice `lightLuxShade` određuje prag
                    iznad kojeg se rolete spuštaju, a ispod kojeg se vraćaju u otvoreniji položaj.
                    `rainProbabilityClose` i `windKphClose` utječu na automatsko zatvaranje prozora.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    name = "Pragovi",
                                    value = """
                                            {
                                              "rainProbabilityClose": 55,
                                              "windKphClose": 45,
                                              "lightLuxShade": 55000,
                                              "indoorTempShadeC": 25,
                                              "blindsShadePosition": 85,
                                              "blindsReleasePosition": 20
                                            }
                                            """
                            )
                    )
            )
    )
    public ResponseEntity<ThresholdUpdateResult> thresholds(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(thresholdService.updateThresholds(payload));
    }

    @PostMapping("/api/commands")
    @Operation(
            summary = "Pošalji ručnu komandu",
            description = """
                    Primjenjuje komandu na lokalno stanje i, za prozor/rolete, dodaje komandu u queue koji ESP32 dohvaća
                    preko `/api/device/commands`. Za `target=automation` akcije `auto` i `manual` samo mijenjaju način rada.
                    Podržani targeti su `window`, `blinds` i `automation`.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @ExampleObject(
                                            name = "Zatvori prozor",
                                            value = """
                                                    {
                                                      "target": "window",
                                                      "action": "close",
                                                      "source": "swagger"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Postavi rolete",
                                            value = """
                                                    {
                                                      "target": "blinds",
                                                      "action": "setPosition",
                                                      "positionPercent": 85,
                                                      "source": "swagger"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Ručni način rada",
                                            value = """
                                                    {
                                                      "target": "automation",
                                                      "action": "manual",
                                                      "source": "swagger"
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    )
    public ResponseEntity<Object> command(@RequestBody CommandRequest request) {
        String source = request.source() == null || request.source().isBlank() ? "web" : request.source();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(commandService.applyCommand(new CommandRequest(
                        request.target(),
                        request.action(),
                        request.positionPercent(),
                        source
                )));
    }

    @GetMapping("/api/device/commands")
    @Operation(
            summary = "Dohvati pending komande za uređaj",
            description = """
                    Endpoint koji koristi ESP32. Vraća komande iz queuea koje još imaju status `pending`.
                    Ako `deviceId` nije poslan, koristi se defaultni `windowsense.device-id` iz konfiguracije.
                    """
    )
    public Map<String, Object> deviceCommands(
            @Parameter(description = "Logički ID uređaja koji dohvaća svoje komande.", example = "windowsense-esp32-01")
            @RequestParam(required = false) String deviceId
    ) {
        return Map.of("commands", commandService.pollCommands(deviceId));
    }

    @PostMapping("/api/device/ack")
    @Operation(
            summary = "Potvrdi izvršenje komande",
            description = """
                    Endpoint koji koristi uređaj nakon što izvrši ili odbije komandu. Ažurira status komande i dodaje
                    event u log. Ako `status` nije poslan, koristi se `acknowledged`.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    name = "Potvrda komande",
                                    value = """
                                            {
                                              "commandId": "cmd-example",
                                              "status": "acknowledged"
                                            }
                                            """
                            )
                    )
            )
    )
    public ResponseEntity<Object> acknowledge(@RequestBody AckRequest request) {
        WindowSenseState.Command command = commandService.acknowledgeCommand(request.commandId(), request.status());
        if (command == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Komanda nije pronadjena."));
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(command);
    }

    private static void sendState(SseEmitter emitter, WindowSenseState state) {
        try {
            emitter.send(SseEmitter.event().name("state").data(state));
        } catch (IOException error) {
            emitter.completeWithError(error);
        }
    }
}
