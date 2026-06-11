package com.windowsense.service;

import com.windowsense.dto.AutomationThresholds;

public class AutomationInput {
    public String mode = "auto";
    public boolean rainDetected;
    public double rainIntensity;
    public double rainProbability;
    public double windKph;
    public int day = 1;
    public double windowOpenPercent;
    public double blindsPositionPercent;
    public AutomationThresholds thresholds = new AutomationThresholds();

    public static AutomationInput createDefault() {
        AutomationInput input = new AutomationInput();
        input.rainDetected = false;
        input.rainIntensity = 0;
        input.rainProbability = 18;
        input.windKph = 12;
        input.day = 1;
        input.windowOpenPercent = 65;
        input.blindsPositionPercent = 30;
        return input;
    }
}
