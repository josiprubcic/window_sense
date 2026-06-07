package com.windowsense.integration.thingsboard;

import java.util.Map;

public record ThingsBoardRpcResult(
        String status,
        Map<String, Object> deviceResponse
) {
    public static ThingsBoardRpcResult executed(Map<String, Object> deviceResponse) {
        return new ThingsBoardRpcResult("EXECUTED", deviceResponse == null ? Map.of() : deviceResponse);
    }

    public static ThingsBoardRpcResult sent(Map<String, Object> deviceResponse) {
        return new ThingsBoardRpcResult("SENT", deviceResponse == null ? Map.of() : deviceResponse);
    }

    public static ThingsBoardRpcResult timeout() {
        return new ThingsBoardRpcResult("TIMEOUT", Map.of());
    }

    public static ThingsBoardRpcResult failed(String message) {
        return new ThingsBoardRpcResult("FAILED", message == null || message.isBlank() ? Map.of() : Map.of("error", message));
    }
}
