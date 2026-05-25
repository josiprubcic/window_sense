package com.windowsense.automation;

import com.windowsense.model.Decision;
import com.windowsense.model.WindowSenseState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AutomationService {

    public List<Decision> evaluate(WindowSenseState state) {
        if (!"auto".equals(state.automation.mode)) {
            return List.of();
        }

        List<Decision> decisions = new ArrayList<>();
        WindowSenseState.Thresholds thresholds = state.automation.thresholds;

        boolean rainRisk = state.sensors.rainDetected
                || state.sensors.rainIntensity > thresholds.rainIntensityClose
                || state.weather.rainProbability >= thresholds.rainProbabilityClose;
        boolean windRisk = state.weather.windKph >= thresholds.windKphClose;
        boolean strongSun = state.sensors.lightLux >= thresholds.lightLuxShade
                && state.sensors.indoorTempC >= thresholds.indoorTempShadeC;
        boolean lowLight = state.sensors.lightLux <= thresholds.lightLuxRelease;

        if ((rainRisk || windRisk) && state.actuators.window.openPercent > 0) {
            decisions.add(new Decision(
                    "window",
                    "close",
                    0.0,
                    rainRisk
                            ? "Detektirana je kisa ili visok rizik oborina."
                            : "Brzina vjetra prelazi sigurni prag."
            ));
        }

        if (strongSun && state.actuators.blinds.positionPercent < thresholds.blindsShadePosition) {
            decisions.add(new Decision(
                    "blinds",
                    "setPosition",
                    thresholds.blindsShadePosition,
                    "Visok intenzitet svjetlosti i temperatura zahtijevaju zasjenu."
            ));
        }

        if (!rainRisk && lowLight && state.actuators.blinds.positionPercent > thresholds.blindsReleasePosition) {
            decisions.add(new Decision(
                    "blinds",
                    "setPosition",
                    thresholds.blindsReleasePosition,
                    "Svjetlost je niska, rolete se vracaju u otvoreniji polozaj."
            ));
        }

        return decisions;
    }

    public Map<String, Object> telemetrySnapshot(WindowSenseState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rainDetected", state.sensors.rainDetected);
        payload.put("rainIntensity", state.sensors.rainIntensity);
        payload.put("lightLux", state.sensors.lightLux);
        payload.put("windowContactOpen", state.sensors.windowContactOpen);
        payload.put("windowOpenPercent", state.actuators.window.openPercent);
        payload.put("blindsPositionPercent", state.actuators.blinds.positionPercent);
        payload.put("indoorTempC", state.sensors.indoorTempC);
        payload.put("outdoorTempC", state.sensors.outdoorTempC);
        payload.put("rainProbability", state.weather.rainProbability);
        payload.put("windKph", state.weather.windKph);
        payload.put("automationMode", state.automation.mode);
        payload.put("batteryPercent", state.sensors.batteryPercent);
        payload.put("signalStrength", state.sensors.signalStrength);
        return payload;
    }
}
