package com.windowsense.service;

import com.windowsense.dto.Decision;

import java.util.List;
import java.util.Map;

public record RoomAutomationEvaluation(
        Map<String, Object> telemetry,
        List<Decision> decisions
) {
}
