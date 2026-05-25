package com.windowsense.api;

import com.windowsense.config.WindowSenseProperties;
import com.windowsense.model.AckRequest;
import com.windowsense.model.CommandRequest;
import com.windowsense.model.TelemetryResult;
import com.windowsense.model.WindowSenseState;
import com.windowsense.store.WindowSenseStore;
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
public class WindowSenseController {

    private final WindowSenseStore store;
    private final WindowSenseProperties properties;

    public WindowSenseController(WindowSenseStore store, WindowSenseProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "ok", true,
                "service", "WindowSense API",
                "deviceId", properties.getDeviceId(),
                "time", WindowSenseState.now()
        );
    }

    @GetMapping("/api/state")
    public WindowSenseState state() {
        return store.getState();
    }

    @GetMapping("/api/events")
    public Map<String, Object> events() {
        return Map.of("events", store.getState().events);
    }

    @GetMapping(path = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() throws IOException {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.send(SseEmitter.event().name("state").data(store.getState()));
        Runnable unsubscribe = store.subscribe(state -> sendState(emitter, state));
        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(unsubscribe);
        emitter.onError(error -> unsubscribe.run());
        return emitter;
    }

    @PostMapping("/api/telemetry")
    public ResponseEntity<TelemetryResult> telemetry(@RequestBody Map<String, Object> payload) {
        String source = payload.get("source") instanceof String text && !text.isBlank() ? text : "device";
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(store.ingestTelemetry(payload, source));
    }

    @PostMapping("/api/weather")
    public ResponseEntity<TelemetryResult> weather(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(store.updateWeather(payload));
    }

    @PostMapping("/api/automation/thresholds")
    public ResponseEntity<TelemetryResult> thresholds(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(store.updateThresholds(payload));
    }

    @PostMapping("/api/commands")
    public ResponseEntity<Object> command(@RequestBody CommandRequest request) {
        String source = request.source() == null || request.source().isBlank() ? "web" : request.source();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(store.applyCommand(new CommandRequest(
                        request.target(),
                        request.action(),
                        request.positionPercent(),
                        source
                )));
    }

    @GetMapping("/api/device/commands")
    public Map<String, Object> deviceCommands(@RequestParam(required = false) String deviceId) {
        return Map.of("commands", store.pollCommands(deviceId));
    }

    @PostMapping("/api/device/ack")
    public ResponseEntity<Object> acknowledge(@RequestBody AckRequest request) {
        WindowSenseState.Command command = store.acknowledgeCommand(request.commandId(), request.status());
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
