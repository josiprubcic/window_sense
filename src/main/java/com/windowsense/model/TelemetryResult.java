package com.windowsense.model;

import java.util.List;

public record TelemetryResult(
        WindowSenseState state,
        List<Decision> decisions
) {
}
