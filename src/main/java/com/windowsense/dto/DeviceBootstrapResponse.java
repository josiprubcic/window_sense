package com.windowsense.dto;

public record DeviceBootstrapResponse(
        String serialNumber,
        String tbDeviceId,
        String thingsBoardHost,
        String thingsBoardMqttHost,
        String thingsBoardAccessToken,
        String commandPollingUrl,
        String commandAckUrl
) {
}
