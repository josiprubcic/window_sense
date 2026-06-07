package com.windowsense.mapper;

import java.util.Map;

public record MappedRoomCommandRpc(
        String method,
        Map<String, Object> params
) {
}
