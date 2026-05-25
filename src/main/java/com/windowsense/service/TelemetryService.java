package com.windowsense.service;

import com.windowsense.automation.AutomationService;
import com.windowsense.model.Decision;
import com.windowsense.model.TelemetryResult;
import com.windowsense.model.WindowSenseState;
import com.windowsense.repository.WindowSenseStateRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TelemetryService {

    private final WindowSenseStateRepository repository;
    private final AutomationService automationService;
    private final CommandService commandService;
    private final EventLogService eventLogService;
    private final StatePublisher statePublisher;

    public TelemetryService(
            WindowSenseStateRepository repository,
            AutomationService automationService,
            CommandService commandService,
            EventLogService eventLogService,
            StatePublisher statePublisher
    ) {
        this.repository = repository;
        this.automationService = automationService;
        this.commandService = commandService;
        this.eventLogService = eventLogService;
        this.statePublisher = statePublisher;
    }

    public TelemetryResult ingestTelemetry(Map<String, Object> payload, String source) {
        TelemetryResult result = repository.withState(state -> {
            state.sensors.rainDetected = PayloadValues.booleanValue(payload, "rainDetected", state.sensors.rainDetected);
            state.sensors.rainIntensity = PayloadValues.numberValue(payload, "rainIntensity", state.sensors.rainIntensity, 0, 100);
            state.sensors.lightLux = PayloadValues.numberValue(payload, "lightLux", state.sensors.lightLux, 0, 120000);
            state.sensors.windowContactOpen = PayloadValues.booleanValue(payload, "windowContactOpen", state.sensors.windowContactOpen);
            state.sensors.indoorTempC = PayloadValues.numberValue(payload, "indoorTempC", state.sensors.indoorTempC, -30, 80);
            state.sensors.outdoorTempC = PayloadValues.numberValue(payload, "outdoorTempC", state.sensors.outdoorTempC, -40, 80);
            state.sensors.batteryPercent = PayloadValues.numberValue(payload, "batteryPercent", state.sensors.batteryPercent, 0, 100);
            state.sensors.signalStrength = PayloadValues.numberValue(payload, "signalStrength", state.sensors.signalStrength, -120, 0);

            if (payload.containsKey("windowOpenPercent")) {
                state.actuators.window.openPercent = PayloadValues.numberValue(
                        payload,
                        "windowOpenPercent",
                        state.actuators.window.openPercent,
                        0,
                        100
                );
                state.sensors.windowContactOpen = state.actuators.window.openPercent > 0;
            }

            if (payload.containsKey("blindsPositionPercent")) {
                state.actuators.blinds.positionPercent = PayloadValues.numberValue(
                        payload,
                        "blindsPositionPercent",
                        state.actuators.blinds.positionPercent,
                        0,
                        100
                );
            }

            boolean weatherChanged = false;
            if (payload.containsKey("rainProbability")) {
                state.weather.rainProbability = PayloadValues.numberValue(
                        payload,
                        "rainProbability",
                        state.weather.rainProbability,
                        0,
                        100
                );
                weatherChanged = true;
            }

            if (payload.containsKey("windKph")) {
                state.weather.windKph = PayloadValues.numberValue(payload, "windKph", state.weather.windKph, 0, 250);
                weatherChanged = true;
            }

            if (payload.get("condition") instanceof String condition && !condition.isBlank()) {
                state.weather.condition = PayloadValues.limit(condition, 80);
                weatherChanged = true;
            }

            if (weatherChanged) {
                touchWeather(state);
            } else {
                touch(state);
            }

            eventLogService.addEvent(state, "info", source, "Zaprimljena telemetrija",
                    "Senzorsko stanje je azurirano.");
            List<Decision> decisions = automationService.evaluate(state);
            commandService.applyAutomationDecisions(state, decisions);
            return new TelemetryResult(state, decisions);
        });

        statePublisher.publish(result.state(), "telemetry");
        return result;
    }

    private void touchWeather(WindowSenseState state) {
        String now = WindowSenseState.now();
        state.weather.updatedAt = now;
        state.updatedAt = now;
    }

    private void touch(WindowSenseState state) {
        state.updatedAt = WindowSenseState.now();
    }
}
