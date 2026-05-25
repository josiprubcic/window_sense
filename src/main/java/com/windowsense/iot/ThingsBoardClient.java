package com.windowsense.iot;

import com.windowsense.automation.AutomationService;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.model.WindowSenseState;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class ThingsBoardClient {

    private final WindowSenseProperties.ThingsBoard properties;
    private final AutomationService automationService;
    private final RestClient restClient;

    public ThingsBoardClient(WindowSenseProperties properties, AutomationService automationService, RestClient.Builder builder) {
        this.properties = properties.getThingsBoard();
        this.automationService = automationService;
        this.restClient = builder.build();
    }

    public Map<String, Object> status() {
        if (properties.getHost().isBlank() || properties.getAccessToken().isBlank()) {
            return Map.of(
                    "connection", "not_configured",
                    "lastSyncAt", "",
                    "lastError", ""
            );
        }

        return Map.of(
                "connection", properties.isSyncEnabled() ? "configured" : "disabled",
                "lastSyncAt", "",
                "lastError", properties.isSyncEnabled() ? "" : "THINGSBOARD_SYNC_ENABLED=false"
        );
    }

    public boolean isReady() {
        return properties.isReady();
    }

    public void sendTelemetry(WindowSenseState state) {
        if (!properties.isReady()) {
            return;
        }

        String token = UriUtils.encodePathSegment(properties.getAccessToken(), StandardCharsets.UTF_8);
        String url = properties.getHost() + "/api/v1/" + token + "/telemetry";
        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(automationService.telemetrySnapshot(state))
                .retrieve()
                .toBodilessEntity();
    }
}
