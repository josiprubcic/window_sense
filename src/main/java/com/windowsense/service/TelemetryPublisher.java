package com.windowsense.service;

import com.windowsense.entity.WindowDevice;

import java.util.Map;

public interface TelemetryPublisher {

    boolean publishTelemetry(WindowDevice device, Map<String, Object> payload);
}
