package com.windowsense.dto;

public record Decision(
        String target,
        String action,
        Double positionPercent,
        String reason
) {
}
