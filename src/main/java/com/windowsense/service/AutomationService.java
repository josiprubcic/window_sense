package com.windowsense.service;

import com.windowsense.dto.AutomationThresholds;
import com.windowsense.dto.Decision;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AutomationService {

    public List<Decision> evaluate(AutomationInput input) {
        if (!"auto".equals(input.mode)) {
            return List.of();
        }

        List<Decision> decisions = new ArrayList<>();
        AutomationThresholds thresholds = input.thresholds;

        boolean rainRisk = input.rainDetected
                || input.rainIntensity > thresholds.rainIntensityClose
                || input.rainProbability >= thresholds.rainProbabilityClose;
        boolean windRisk = input.windKph >= thresholds.windKphClose;
        boolean day = input.day > 0;

        if ((rainRisk || windRisk) && input.windowOpenPercent > 0) {
            decisions.add(new Decision(
                    "window",
                    "close",
                    0.0,
                    rainRisk
                            ? "Detektirana je kisa ili visok rizik oborina."
                            : "Brzina vjetra prelazi sigurni prag."
            ));
        }

        if (day && input.blindsPositionPercent < thresholds.blindsShadePosition) {
            decisions.add(new Decision(
                    "blinds",
                    "setPosition",
                    thresholds.blindsShadePosition,
                    "Dan je aktivan, rolete se spustaju u zadani dnevni polozaj."
            ));
        }

        if (!day && input.blindsPositionPercent > thresholds.blindsReleasePosition) {
            decisions.add(new Decision(
                    "blinds",
                    "setPosition",
                    thresholds.blindsReleasePosition,
                    "Noc je aktivna, rolete se vracaju u zadani nocni polozaj."
            ));
        }

        return decisions;
    }
}
