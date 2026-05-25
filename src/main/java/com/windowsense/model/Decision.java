package com.windowsense.model;

public record Decision(
        String target,
        String action,
        Double positionPercent,
        String reason
) {
}
