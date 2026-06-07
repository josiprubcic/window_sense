package com.windowsense.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RoomAutomationThresholdsResponse(
        UUID roomId,
        AutomationThresholds thresholds,
        Map<String, Object> telemetry,
        List<Decision> decisions
) {
}
