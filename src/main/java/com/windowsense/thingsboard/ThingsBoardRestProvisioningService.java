package com.windowsense.thingsboard;

import com.windowsense.common.ThingsBoardProvisioningException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.config.WindowSenseProperties.ProvisioningAuthMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "windowsense.things-board", name = "provisioning-enabled", havingValue = "true")
public class ThingsBoardRestProvisioningService implements ThingsBoardProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ThingsBoardRestProvisioningService.class);

    private static final String ASSET_TYPE = "WindowSense Room";
    private static final String DEVICE_TYPE = "WindowSense Virtual Window";
    private static final String AUTH_HEADER = "X-Authorization";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE = new ParameterizedTypeReference<>() {
    };

    private final WindowSenseProperties.ThingsBoard properties;
    private final RestClient restClient;

    public ThingsBoardRestProvisioningService(WindowSenseProperties properties, RestClient.Builder builder) {
        this.properties = properties.getThingsBoard();
        this.restClient = builder.build();
    }

    @Override
    public ProvisionedRoomDevice provisionVirtualRoomDevice(VirtualRoomProvisioningRequest request) {
        if (!properties.isProvisioningReady()) {
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nije ispravno konfiguriran.");
        }

        try {
            String authorization = authorizationHeader();
            String suffix = request.roomId().toString().substring(0, 8);
            String tbAssetName = "WindowSense Room - " + request.roomName() + " [" + suffix + "]";
            String tbDeviceName = request.deviceName() + " [" + suffix + "]";

            String assetId = createAsset(authorization, tbAssetName, request.roomName());
            String deviceId = createDevice(authorization, tbDeviceName, request.deviceName());
            createRelation(authorization, assetId, deviceId);
            saveDeviceAttributes(authorization, deviceId, request);

            // TODO: Persist ThingsBoard device credentials only after encrypted storage is added.
            return new ProvisionedRoomDevice(assetId, deviceId);
        } catch (HttpClientErrorException.Unauthorized error) {
            log.warn("ThingsBoard provisioning failed with HTTP 401.");
            if (properties.getProvisioningAuthMode() == ProvisioningAuthMode.JWT) {
                throw new ThingsBoardProvisioningException("ThingsBoard JWT token je istekao ili nije valjan. Obnovite THINGSBOARD_JWT_TOKEN.", error);
            }
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning autentifikacija nije uspjela.", error);
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard provisioning failed: {}", error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nije uspio.", error);
        }
    }

    @Override
    public void markRoomDeviceDeleted(String tbAssetId, String tbDeviceId) {
        if (!properties.isProvisioningReady()) {
            return;
        }

        try {
            String authorization = authorizationHeader();
            Map<String, Object> attributes = Map.of(
                    "active", false,
                    "deletedFromApp", true
            );
            saveAttributes(authorization, "ASSET", tbAssetId, "SERVER_SCOPE", attributes);
            saveAttributes(authorization, "DEVICE", tbDeviceId, "SERVER_SCOPE", attributes);
            saveAttributes(authorization, "DEVICE", tbDeviceId, "SHARED_SCOPE", attributes);
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard soft delete marker failed for asset {} and device {}: {}", tbAssetId, tbDeviceId, error.getClass().getSimpleName());
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

        Object token = value(response, "token");
        if (token == null || token.toString().isBlank()) {
            throw new IllegalArgumentException("ThingsBoard login response ne sadrzi token.");
        }
        return token.toString();
    }

    private String createAsset(String authorization, String name, String label) {
        Map<String, Object> response = restClient.post()
                .uri(properties.getHost() + "/api/asset")
                .header(AUTH_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", name,
                        "label", label,
                        "type", ASSET_TYPE
                ))
                .retrieve()
                .body(MAP_RESPONSE);

        return entityId(response, "Asset");
    }

    private String createDevice(String authorization, String name, String label) {
        Map<String, Object> response = restClient.post()
                .uri(properties.getHost() + "/api/device")
                .header(AUTH_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", name,
                        "label", label,
                        "type", DEVICE_TYPE
                ))
                .retrieve()
                .body(MAP_RESPONSE);

        return entityId(response, "Device");
    }

    private void createRelation(String authorization, String assetId, String deviceId) {
        restClient.post()
                .uri(properties.getHost() + "/api/relation")
                .header(AUTH_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", Map.of(
                                "entityType", "ASSET",
                                "id", assetId
                        ),
                        "to", Map.of(
                                "entityType", "DEVICE",
                                "id", deviceId
                        ),
                        "type", "Contains",
                        "typeGroup", "COMMON"
                ))
                .retrieve()
                .toBodilessEntity();
    }

    private void saveDeviceAttributes(String authorization, String deviceId, VirtualRoomProvisioningRequest request) {
        Map<String, Object> attributes = Map.of(
                "isVirtual", true,
                "roomId", request.roomId().toString(),
                "roomName", request.roomName(),
                "appUserId", request.appUserId().toString(),
                "auth0Sub", request.auth0Sub(),
                "deviceType", "VIRTUAL",
                "active", true,
                "deletedFromApp", false
        );

        saveAttributes(authorization, "DEVICE", deviceId, "SERVER_SCOPE", attributes);
        saveAttributes(authorization, "DEVICE", deviceId, "SHARED_SCOPE", attributes);
    }

    private void saveAttributes(String authorization, String entityType, String entityId, String scope, Map<String, Object> attributes) {
        restClient.post()
                .uri(properties.getHost() + "/api/plugins/telemetry/" + entityType + "/" + entityId + "/attributes/" + scope)
                .header(AUTH_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(attributes)
                .retrieve()
                .toBodilessEntity();
    }

    private static String entityId(Map<String, Object> response, String entityName) {
        Object id = value(response, "id");
        if (id instanceof Map<?, ?> idMap) {
            Object rawId = idMap.get("id");
            if (rawId != null && !rawId.toString().isBlank()) {
                return rawId.toString();
            }
        }

        throw new IllegalArgumentException(entityName + " response ne sadrzi entity id.");
    }

    private static Object value(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }
}
