package com.windowsense.service;

public record VirtualWeatherSample(
        boolean rainDetected,
        int rainIntensity,
        int rainRiskPercent,
        int lux,
        double indoorTempC,
        int windKmh,
        int windowOpenPercent,
        int blindClosedPercent
) {
}
