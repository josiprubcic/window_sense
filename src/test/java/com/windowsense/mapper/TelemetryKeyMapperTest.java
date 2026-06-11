package com.windowsense.mapper;

import com.windowsense.mapper.TelemetryKeyMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryKeyMapperTest {

    private final TelemetryKeyMapper mapper = new TelemetryKeyMapper();

    @Test
    void mapsLegacyKeysToCanonicalKeys() {
        Map<String, Object> normalized = mapper.normalizeRoomTelemetry(Map.of(
                "rainProbability", 63,
                "windKph", 12,
                "blindsPositionPercent", 72
        ), Instant.parse("2026-06-03T10:15:30Z"));

        assertThat(normalized)
                .containsEntry("rainRiskPercent", 63)
                .containsEntry("windKmh", 12)
                .containsEntry("blindClosedPercent", 72)
                .containsEntry("lastUpdatedAt", "2026-06-03T10:15:30Z");
    }

    @Test
    void prefersCanonicalKeysWhenBothExist() {
        Map<String, Object> normalized = mapper.normalizeRoomTelemetry(Map.of(
                "rainRiskPercent", 44,
                "rainProbability", 63,
                "windKmh", 9,
                "windKph", 12,
                "blindClosedPercent", 21,
                "blindsPositionPercent", 72
        ), null);

        assertThat(normalized)
                .containsEntry("rainRiskPercent", 44)
                .containsEntry("windKmh", 9)
                .containsEntry("blindClosedPercent", 21)
                .doesNotContainKey("lastUpdatedAt");
    }

    @Test
    void handlesMissingKeysSafely() {
        Map<String, Object> normalized = mapper.normalizeRoomTelemetry(Map.of("rainDetected", true), null);

        assertThat(normalized)
                .containsEntry("rainDetected", true)
                .doesNotContainKeys("rainRiskPercent", "windKmh", "blindClosedPercent");
    }
}
