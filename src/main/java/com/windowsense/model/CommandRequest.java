package com.windowsense.model;

public record CommandRequest(
        String target,
        String action,
        Double positionPercent,
        String source
) {
}
