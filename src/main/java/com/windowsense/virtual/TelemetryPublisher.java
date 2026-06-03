package com.windowsense.virtual;

import com.windowsense.device.WindowDevice;

import java.util.Map;

public interface TelemetryPublisher {

    void publishTelemetry(WindowDevice device, Map<String, Object> payload);
}
