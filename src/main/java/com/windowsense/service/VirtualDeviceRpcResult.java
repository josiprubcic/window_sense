package com.windowsense.service;

import java.util.Map;

public record VirtualDeviceRpcResult(
        Map<String, Object> response,
        Map<String, Object> telemetry
) {
}
