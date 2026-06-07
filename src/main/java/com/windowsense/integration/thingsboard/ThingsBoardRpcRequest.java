package com.windowsense.integration.thingsboard;

import java.util.Map;

public record ThingsBoardRpcRequest(
        String method,
        Map<String, Object> params,
        long timeout,
        boolean persistent
) {
}
