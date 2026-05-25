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
public class WeatherService {

    private final WindowSenseStateRepository repository;
    private final AutomationService automationService;
    private final CommandService commandService;
    private final EventLogService eventLogService;
    private final StatePublisher statePublisher;

    public WeatherService(
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

    public TelemetryResult updateWeather(Map<String, Object> payload) {
        TelemetryResult result = repository.withState(state -> {
            if (payload.get("condition") instanceof String condition && !condition.isBlank()) {
                state.weather.condition = PayloadValues.limit(condition, 80);
            }

            state.weather.rainProbability = PayloadValues.numberValue(
                    payload,
                    "rainProbability",
                    state.weather.rainProbability,
                    0,
                    100
            );
            state.weather.windKph = PayloadValues.numberValue(payload, "windKph", state.weather.windKph, 0, 250);
            state.weather.forecastSource = payload.get("forecastSource") instanceof String source && !source.isBlank()
                    ? PayloadValues.limit(source, 80)
                    : "api";
            touchWeather(state);
            eventLogService.addEvent(state, "info", "weather", "Prognoza azurirana",
                    state.weather.condition + ", " + Math.round(state.weather.rainProbability) + "% rizika kise.");
            List<Decision> decisions = automationService.evaluate(state);
            commandService.applyAutomationDecisions(state, decisions);
            return new TelemetryResult(state, decisions);
        });

        statePublisher.publish(result.state(), "weather");
        return result;
    }

    private void touchWeather(WindowSenseState state) {
        String now = WindowSenseState.now();
        state.weather.updatedAt = now;
        state.updatedAt = now;
    }
}
