package com.windowsense.mapper;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TelemetryKeyMapper {

    public Map<String, Object> normalizeRoomTelemetry(Map<String, Object> telemetry, Instant updatedAt) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (telemetry == null || telemetry.isEmpty()) {
            return normalized;
        }

        copyFirstPresent(normalized, telemetry, "rainDetected", "rainDetected");
        copyFirstPresent(normalized, telemetry, "rainIntensity", "rainIntensity");
        copyFirstPresent(normalized, telemetry, "rainRiskPercent", "rainRiskPercent", "rainProbability");
        copyFirstPresent(normalized, telemetry, "lux", "lux", "lightLux");
        copyFirstPresent(normalized, telemetry, "indoorTempC", "indoorTempC");
        copyFirstPresent(normalized, telemetry, "windKmh", "windKmh", "windKph");
        copyFirstPresent(normalized, telemetry, "windowOpenPercent", "windowOpenPercent");
        copyFirstPresent(normalized, telemetry, "blindClosedPercent", "blindClosedPercent", "blindsPositionPercent");

        if (updatedAt != null) {
            normalized.put("lastUpdatedAt", updatedAt.toString());
        }

        return normalized;
    }

    private void copyFirstPresent(
            Map<String, Object> normalized,
            Map<String, Object> telemetry,
            String canonicalKey,
            String... candidateKeys
    ) {
        for (String candidateKey : candidateKeys) {
            if (telemetry.containsKey(candidateKey) && telemetry.get(candidateKey) != null) {
                normalized.put(canonicalKey, telemetry.get(candidateKey));
                return;
            }
        }
    }
}
