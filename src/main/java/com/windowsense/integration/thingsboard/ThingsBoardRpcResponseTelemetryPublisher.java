package com.windowsense.integration.thingsboard;

import com.windowsense.config.WindowSenseProperties;
import com.windowsense.entity.WindowDevice;
import com.windowsense.exception.EncryptionException;
import com.windowsense.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
public class ThingsBoardRpcResponseTelemetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(ThingsBoardRpcResponseTelemetryPublisher.class);

    private final WindowSenseProperties.ThingsBoard properties;
    private final EncryptionService encryptionService;
    private final RestClient restClient;

    public ThingsBoardRpcResponseTelemetryPublisher(
            WindowSenseProperties properties,
            EncryptionService encryptionService,
            RestClient.Builder builder
    ) {
        this.properties = properties.getThingsBoard();
        this.encryptionService = encryptionService;
        this.restClient = builder.build();
    }

    public boolean publish(WindowDevice device, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        if (properties.getHost().isBlank()) {
            log.warn("ThingsBoard host nije konfiguriran; preskacem RPC response telemetry za uredjaj {}.", device.getId());
            return false;
        }

        String encryptedToken = device.getTbDeviceTokenEncrypted();
        if (encryptedToken == null || encryptedToken.isBlank()) {
            log.warn("Uredjaj {} nema spremljen ThingsBoard access token; preskacem RPC response telemetry.", device.getId());
            return false;
        }

        String token;
        try {
            token = encryptionService.decrypt(encryptedToken);
        } catch (EncryptionException error) {
            log.warn("ThingsBoard access token za uredjaj {} nije moguce dekriptirati; preskacem RPC response telemetry.", device.getId());
            return false;
        }

        try {
            restClient.post()
                    .uri(properties.getHost() + "/api/v1/" + token + "/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("RPC response telemetry poslana za uredjaj {} na ThingsBoard device {}.", device.getId(), device.getTbDeviceId());
            return true;
        } catch (HttpStatusCodeException error) {
            log.warn("RPC response telemetry publish nije uspio za uredjaj {}. HTTP status: {}.",
                    device.getId(),
                    error.getStatusCode().value());
        } catch (RestClientException error) {
            log.warn("RPC response telemetry publish nije uspio za uredjaj {}: {}.",
                    device.getId(),
                    error.getClass().getSimpleName());
        }
        return false;
    }
}
