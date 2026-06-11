package com.windowsense.service;

import com.windowsense.service.AutomationService;
import com.windowsense.service.AutomationInput;
import com.windowsense.dto.Decision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationServiceTest {

    private final AutomationService automationService = new AutomationService();

    @Test
    void closesWindowWhenRainIsDetected() {
        AutomationInput input = AutomationInput.createDefault();
        input.rainDetected = true;
        input.windowOpenPercent = 70;

        List<Decision> decisions = automationService.evaluate(input);

        assertThat(decisions)
                .anySatisfy(decision -> {
                    assertThat(decision.target()).isEqualTo("window");
                    assertThat(decision.action()).isEqualTo("close");
                    assertThat(decision.positionPercent()).isEqualTo(0);
                });
    }

    @Test
    void lowersBlindsDuringDay() {
        AutomationInput input = AutomationInput.createDefault();
        input.day = 1;
        input.blindsPositionPercent = 10;

        List<Decision> decisions = automationService.evaluate(input);

        assertThat(decisions)
                .anySatisfy(decision -> {
                    assertThat(decision.target()).isEqualTo("blinds");
                    assertThat(decision.action()).isEqualTo("setPosition");
                    assertThat(decision.positionPercent()).isEqualTo(85);
                });
    }

    @Test
    void raisesBlindsDuringNight() {
        AutomationInput input = AutomationInput.createDefault();
        input.day = 0;
        input.blindsPositionPercent = 85;

        List<Decision> decisions = automationService.evaluate(input);

        assertThat(decisions)
                .anySatisfy(decision -> {
                    assertThat(decision.target()).isEqualTo("blinds");
                    assertThat(decision.action()).isEqualTo("setPosition");
                    assertThat(decision.positionPercent()).isEqualTo(20);
                });
    }

    @Test
    void manualModeDoesNotCreateAutomationDecisions() {
        AutomationInput input = AutomationInput.createDefault();
        input.mode = "manual";
        input.rainDetected = true;
        input.windowOpenPercent = 70;

        assertThat(automationService.evaluate(input)).isEmpty();
    }
}
