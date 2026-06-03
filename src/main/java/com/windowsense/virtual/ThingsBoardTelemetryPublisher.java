package com.windowsense.virtual;

import com.windowsense.common.EncryptionException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.device.WindowDevice;
import com.windowsense.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "windowsense.virtual-simulator", name = "enabled", havingValue = "true")
public class ThingsBoardTelemetryPublisher implements TelemetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(ThingsBoardTelemetryPublisher.class);

    private final WindowSenseProperties.ThingsBoard properties;
    private final EncryptionService encryptionService;
    private final RestClient restClient;

    public ThingsBoardTelemetryPublisher(
            WindowSenseProperties properties,
            EncryptionService encryptionService,
            RestClient.Builder builder
    ) {
        this.properties = properties.getThingsBoard();
        this.encryptionService = encryptionService;
        this.restClient = builder.build();
    }

    @Override
    public void publishTelemetry(WindowDevice device, Map<String, Object> payload) {
        if (properties.getHost().isBlank()) {
            log.warn("ThingsBoard host nije konfiguriran; preskacem virtualnu telemetriju za uredjaj {}.", device.getId());
            return;
        }

        String encryptedToken = device.getTbDeviceTokenEncrypted();
        if (encryptedToken == null || encryptedToken.isBlank()) {
            log.warn("Virtualni uredjaj {} nema spremljen ThingsBoard access token; preskacem telemetriju.", device.getId());
            return;
        }

        String token;
        try {
            token = encryptionService.decrypt(encryptedToken);
        } catch (EncryptionException error) {
            log.warn("ThingsBoard access token za uredjaj {} nije moguce dekriptirati; preskacem telemetriju.", device.getId());
            return;
        }

        try {
            restClient.post()
                    .uri(properties.getHost() + "/api/v1/" + token + "/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException error) {
            log.warn("ThingsBoard telemetry publish nije uspio za uredjaj {}. HTTP status: {}.",
                    device.getId(),
                    error.getStatusCode().value());
        } catch (RestClientException error) {
            log.warn("ThingsBoard telemetry publish nije uspio za uredjaj {}: {}.",
                    device.getId(),
                    error.getClass().getSimpleName());
        }
    }
}
