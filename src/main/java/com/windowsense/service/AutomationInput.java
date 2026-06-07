package com.windowsense.service;

import com.windowsense.dto.AutomationThresholds;

public class AutomationInput {
    public String mode = "auto";
    public boolean rainDetected;
    public double rainIntensity;
    public double rainProbability;
    public double windKph;
    public double lightLux;
    public double indoorTempC;
    public double windowOpenPercent;
    public double blindsPositionPercent;
    public AutomationThresholds thresholds = new AutomationThresholds();

    public static AutomationInput createDefault() {
        AutomationInput input = new AutomationInput();
        input.rainDetected = false;
        input.rainIntensity = 0;
        input.rainProbability = 18;
        input.windKph = 12;
        input.lightLux = 42000;
        input.indoorTempC = 24;
        input.windowOpenPercent = 65;
        input.blindsPositionPercent = 30;
        return input;
    }
}
