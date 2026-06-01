package com.windowsense.model;

import java.util.List;

public record ThresholdUpdateResult(
        WindowSenseState.Thresholds thresholds,
        List<Decision> decisions,
        String updatedAt
) {
}
