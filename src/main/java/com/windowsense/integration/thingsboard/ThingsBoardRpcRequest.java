package com.windowsense.integration.thingsboard;

public record ThingsBoardRpcRequest(
        String method,
        Object params,
        long timeout,
        boolean persistent
) {
}
