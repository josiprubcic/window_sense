package com.windowsense.service;

import com.windowsense.automation.AutomationService;
import com.windowsense.model.Decision;
import com.windowsense.model.ThresholdUpdateResult;
import com.windowsense.model.WindowSenseState;
import com.windowsense.repository.WindowSenseStateRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ThresholdService {

    private final WindowSenseStateRepository repository;
    private final AutomationService automationService;
    private final CommandService commandService;
    private final EventLogService eventLogService;
    private final StatePublisher statePublisher;

    public ThresholdService(
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

    public ThresholdUpdateResult updateThresholds(Map<String, Object> payload) {
        ThresholdUpdateResult result = repository.withState(state -> {
            WindowSenseState.Thresholds thresholds = state.automation.thresholds;
            thresholds.rainIntensityClose = PayloadValues.threshold(
                    payload,
                    "rainIntensityClose",
                    thresholds.rainIntensityClose,
                    0,
                    100
            );
            thresholds.rainProbabilityClose = PayloadValues.threshold(
                    payload,
                    "rainProbabilityClose",
                    thresholds.rainProbabilityClose,
                    0,
                    100
            );
            thresholds.windKphClose = PayloadValues.threshold(payload, "windKphClose", thresholds.windKphClose, 0, 250);
            thresholds.lightLuxShade = PayloadValues.threshold(payload, "lightLuxShade", thresholds.lightLuxShade, 0, 120000);
            thresholds.lightLuxRelease = PayloadValues.threshold(
                    payload,
                    "lightLuxRelease",
                    thresholds.lightLuxRelease,
                    0,
                    120000
            );
            thresholds.indoorTempShadeC = PayloadValues.threshold(
                    payload,
                    "indoorTempShadeC",
                    thresholds.indoorTempShadeC,
                    -30,
                    80
            );
            thresholds.blindsShadePosition = PayloadValues.threshold(
                    payload,
                    "blindsShadePosition",
                    thresholds.blindsShadePosition,
                    0,
                    100
            );
            thresholds.blindsReleasePosition = PayloadValues.threshold(
                    payload,
                    "blindsReleasePosition",
                    thresholds.blindsReleasePosition,
                    0,
                    100
            );

            touch(state);
            eventLogService.addEvent(state, "info", "api", "Pragovi azurirani",
                    "Pravila automatizacije su promijenjena.");
            List<Decision> decisions = automationService.evaluate(state);
            commandService.applyAutomationDecisions(state, decisions);
            return new ThresholdUpdateResult(state.automation.thresholds, decisions, state.updatedAt);
        });

        statePublisher.publish(repository.getState(), "thresholds");
        return result;
    }

    private void touch(WindowSenseState state) {
        state.updatedAt = WindowSenseState.now();
    }
}
