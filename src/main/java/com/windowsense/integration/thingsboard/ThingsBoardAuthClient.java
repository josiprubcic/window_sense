package com.windowsense.integration.thingsboard;

import com.windowsense.config.WindowSenseProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class ThingsBoardAuthClient {

    public static final String AUTH_HEADER = "X-Authorization";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE = new ParameterizedTypeReference<>() {
    };

    private final WindowSenseProperties.ThingsBoard properties;
    private final RestClient restClient;

    public ThingsBoardAuthClient(WindowSenseProperties properties, RestClient.Builder builder) {
        this.properties = properties.getThingsBoard();
        this.restClient = builder.build();
    }

    public String authorizationHeader() {
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
}
