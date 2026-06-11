package com.windowsense.integration.thingsboard;

import com.windowsense.exception.ThingsBoardProvisioningException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.config.WindowSenseProperties.ProvisioningAuthMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ThingsBoardTelemetryQueryService {

    private static final Logger log = LoggerFactory.getLogger(ThingsBoardTelemetryQueryService.class);

    private static final String AUTH_HEADER = "X-Authorization";
    private static final String KEYS = String.join(",",
            "rainDetected",
            "rainIntensity",
            "rainRiskPercent",
            "rainProbability",
            "windKmh",
            "windKph",
            "windowOpenPercent",
            "blindClosedPercent",
            "blindsPositionPercent",
            "roomId",
            "roomName",
            "isVirtual"
    );
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Map<String, List<Map<String, Object>>>> TELEMETRY_RESPONSE = new ParameterizedTypeReference<>() {
    };

    private final WindowSenseProperties.ThingsBoard properties;
    private final RestClient restClient;

    public ThingsBoardTelemetryQueryService(WindowSenseProperties properties, RestClient.Builder builder) {
        this.properties = properties.getThingsBoard();
        this.restClient = builder.build();
    }

    public LatestTelemetry latestDeviceTelemetry(String tbDeviceId) {
        if (!properties.isProvisioningReady()) {
            throw new ThingsBoardProvisioningException("ThingsBoard REST autentifikacija nije ispravno konfigurirana.");
        }

        try {
            Map<String, List<Map<String, Object>>> response = restClient.get()
                    .uri(properties.getHost() + "/api/plugins/telemetry/DEVICE/" + tbDeviceId + "/values/timeseries?keys=" + KEYS)
                    .header(AUTH_HEADER, authorizationHeader())
                    .retrieve()
                    .body(TELEMETRY_RESPONSE);

            return parse(response);
        } catch (HttpClientErrorException.Unauthorized error) {
            log.warn("ThingsBoard latest telemetry query failed with HTTP 401.");
            if (properties.getProvisioningAuthMode() == ProvisioningAuthMode.JWT) {
                throw new ThingsBoardProvisioningException("ThingsBoard JWT token je istekao ili nije valjan. Obnovite THINGSBOARD_JWT_TOKEN.", error);
            }
            throw new ThingsBoardProvisioningException("ThingsBoard telemetry autentifikacija nije uspjela.", error);
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard latest telemetry query failed for device {}: {}", tbDeviceId, error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard latest telemetry nije dostupna.", error);
        }
    }

    private LatestTelemetry parse(Map<String, List<Map<String, Object>>> response) {
        Map<String, Object> telemetry = new LinkedHashMap<>();
        long latestTs = 0;
        if (response == null) {
            return new LatestTelemetry(telemetry, null);
        }

        for (Map.Entry<String, List<Map<String, Object>>> entry : response.entrySet()) {
            List<Map<String, Object>> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }

            Map<String, Object> latest = values.getFirst();
            telemetry.put(entry.getKey(), parseValue(latest.get("value")));
            Object ts = latest.get("ts");
            if (ts instanceof Number number) {
                latestTs = Math.max(latestTs, number.longValue());
            } else if (ts != null) {
                latestTs = Math.max(latestTs, Long.parseLong(ts.toString()));
            }
        }

        return new LatestTelemetry(
                telemetry,
                latestTs > 0 ? Instant.ofEpochMilli(latestTs) : null
        );
    }

    private Object parseValue(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }

        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.parseBoolean(text);
        }

        try {
            if (text.contains(".")) {
                return Double.parseDouble(text);
            }
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return text;
        }
    }

    private String authorizationHeader() {
        return switch (properties.getProvisioningAuthMode()) {
            case PASSWORD -> "Bearer " + authenticateWithPassword();
            case JWT -> "Bearer " + properties.getJwtToken();
            case API_KEY -> "ApiKey " + properties.getApiKey();
        };
    }

    private String authenticateWithPassword() {
        Map<String, Object> response = restClient.post()
                .uri(properties.getHost() + "/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", properties.getUsername(),
                        "password", properties.getPassword()
                ))
                .retrieve()
                .body(MAP_RESPONSE);

        Object token = response == null ? null : response.get("token");
        if (token == null || token.toString().isBlank()) {
            throw new IllegalArgumentException("ThingsBoard login response ne sadrzi token.");
        }
        return token.toString();
    }

    public record LatestTelemetry(
            Map<String, Object> telemetry,
            Instant updatedAt
    ) {
    }
}
