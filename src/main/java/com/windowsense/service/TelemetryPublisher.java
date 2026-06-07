package com.windowsense.service;

import com.windowsense.entity.WindowDevice;

import java.util.Map;

public interface TelemetryPublisher {

    void publishTelemetry(WindowDevice device, Map<String, Object> payload);
}
