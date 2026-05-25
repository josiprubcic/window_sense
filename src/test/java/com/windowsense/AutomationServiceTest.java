package com.windowsense;

import com.windowsense.automation.AutomationService;
import com.windowsense.model.Decision;
import com.windowsense.model.WindowSenseState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationServiceTest {

    private final AutomationService automationService = new AutomationService();

    @Test
    void closesWindowWhenRainIsDetected() {
        WindowSenseState state = WindowSenseState.createDefault("test-device");
        state.sensors.rainDetected = true;
        state.actuators.window.openPercent = 70;

        List<Decision> decisions = automationService.evaluate(state);

        assertThat(decisions)
                .anySatisfy(decision -> {
                    assertThat(decision.target()).isEqualTo("window");
                    assertThat(decision.action()).isEqualTo("close");
                    assertThat(decision.positionPercent()).isEqualTo(0);
                });
    }

    @Test
    void lowersBlindsOnStrongLightAndHighIndoorTemperature() {
        WindowSenseState state = WindowSenseState.createDefault("test-device");
        state.sensors.lightLux = 80000;
        state.sensors.indoorTempC = 27;
        state.actuators.blinds.positionPercent = 10;

        List<Decision> decisions = automationService.evaluate(state);

        assertThat(decisions)
                .anySatisfy(decision -> {
                    assertThat(decision.target()).isEqualTo("blinds");
                    assertThat(decision.action()).isEqualTo("setPosition");
                    assertThat(decision.positionPercent()).isEqualTo(85);
                });
    }

    @Test
    void manualModeDoesNotCreateAutomationDecisions() {
        WindowSenseState state = WindowSenseState.createDefault("test-device");
        state.automation.mode = "manual";
        state.sensors.rainDetected = true;
        state.actuators.window.openPercent = 70;

        assertThat(automationService.evaluate(state)).isEmpty();
    }
}
