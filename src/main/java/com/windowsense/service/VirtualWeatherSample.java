package com.windowsense.service;

public record VirtualWeatherSample(
        boolean rainDetected,
        int rainIntensity,
        int rainRiskPercent,
        int lux,
        double indoorTempC,
        int windKmh,
        int windowOpenPercent,
        int blindClosedPercent,
        int day
) {
    public VirtualWeatherSample(
            boolean rainDetected,
            int rainIntensity,
            int rainRiskPercent,
            int lux,
            double indoorTempC,
            int windKmh,
            int windowOpenPercent,
            int blindClosedPercent
    ) {
        this(rainDetected, rainIntensity, rainRiskPercent, lux, indoorTempC, windKmh, windowOpenPercent, blindClosedPercent, 0);
    }
}
