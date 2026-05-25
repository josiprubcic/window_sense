package com.windowsense.events;

import com.windowsense.model.WindowSenseState;

public record StateChangedEvent(
        WindowSenseState state,
        String reason
) {
}
