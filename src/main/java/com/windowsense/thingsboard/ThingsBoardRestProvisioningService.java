package com.windowsense.thingsboard;

import com.windowsense.common.ThingsBoardProvisioningException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.config.WindowSenseProperties.ProvisioningAuthMode;
import com.windowsense.config.WindowSenseProperties.ThingsBoardDeleteMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Map;
import java.util.function.Supplier;

@Service
@ConditionalOnProperty(prefix = "windowsense.things-board", name = "provisioning-enabled", havingValue = "true")
public class ThingsBoardRestProvisioningService implements ThingsBoardProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ThingsBoardRestProvisioningService.class);

    private static final String ASSET_TYPE = "WindowSense Room";
    private static final String DEVICE_TYPE = "WindowSense Virtual Window";
    private static final String PENDING_THINGSBOARD_ID = "pending-thingsboard-provisioning";
    private static final String MOCK_THINGSBOARD_ASSET_PREFIX = "tb-asset-";
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

            String assetId = provisioningStep("create asset", () -> createAsset(authorization, tbAssetName, request.roomName()));
            String deviceId = provisioningStep("create device", () -> createDevice(authorization, tbDeviceName, request.deviceName()));
            String deviceAccessToken = provisioningStep("fetch device credentials", () -> fetchDeviceAccessToken(authorization, deviceId));
            provisioningStep("create relation", () -> {
                createRelation(authorization, assetId, deviceId);
                return null;
            });
            provisioningStep("save device attributes", () -> {
                saveDeviceAttributes(authorization, deviceId, request);
                return null;
            });

            return new ProvisionedRoomDevice(assetId, deviceId, deviceAccessToken);
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard provisioning failed: {}", error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nije uspio.", error);
        }
    }

    @Override
    public void linkExistingPhysicalDevice(ExistingPhysicalDeviceLinkRequest request) {
        if (request.tbDeviceId() == null || request.tbDeviceId().isBlank()) {
            throw new IllegalArgumentException("ThingsBoard Device ID je obavezan.");
        }

        if (!properties.isProvisioningReady()) {
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nije ispravno konfiguriran.");
        }

        try {
            String authorization = authorizationHeader();
            provisioningStep("verify existing physical device", () -> {
                verifyDeviceExists(authorization, request.tbDeviceId());
                return null;
            });
            if (isLinkableAssetId(request.tbAssetId())) {
                provisioningStep("create physical device relation", () -> {
                    createRelation(authorization, request.tbAssetId(), request.tbDeviceId());
                    return null;
                });
            }
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard physical device link failed for room {}: {}", request.roomId(), error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard povezivanje fizickog uredjaja nije uspjelo.", error);
        }
    }

    @Override
    public void deprovisionVirtualRoom(VirtualRoomDeprovisioningRequest request) {
        if (!properties.isProvisioningReady()) {
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nije ispravno konfiguriran.");
        }

        try {
            String authorization = authorizationHeader();
            if (properties.getDeleteMode() == ThingsBoardDeleteMode.HARD) {
                hardDeleteRoom(authorization, request);
                return;
            }

            softDeleteRoom(authorization, request);
        } catch (HttpClientErrorException.Unauthorized error) {
            log.warn("ThingsBoard deprovisioning failed with HTTP 401.");
            if (properties.getProvisioningAuthMode() == ProvisioningAuthMode.JWT) {
                throw new ThingsBoardProvisioningException("ThingsBoard JWT token je istekao ili nije valjan. Obnovite THINGSBOARD_JWT_TOKEN.", error);
            }
            throw new ThingsBoardProvisioningException("ThingsBoard deprovisioning autentifikacija nije uspjela.", error);
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard deprovisioning failed for room {}: {}", request.roomId(), error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard deprovisioning nije uspio.", error);
        }
    }

    private void softDeleteRoom(String authorization, VirtualRoomDeprovisioningRequest request) {
        Map<String, Object> attributes = Map.of(
                "active", false,
                "deletedFromApp", true
        );
        deprovisioningStep("mark asset deleted", () -> saveAttributes(authorization, "ASSET", request.tbAssetId(), "SERVER_SCOPE", attributes));
        deprovisioningStep("mark device deleted in server scope", () -> saveAttributes(authorization, "DEVICE", request.tbDeviceId(), "SERVER_SCOPE", attributes));
        deprovisioningStep("mark device deleted in shared scope", () -> saveAttributes(authorization, "DEVICE", request.tbDeviceId(), "SHARED_SCOPE", attributes));
    }

    private void hardDeleteRoom(String authorization, VirtualRoomDeprovisioningRequest request) {
        deleteRelationBestEffort(authorization, request);
        deprovisioningStep("delete device", () -> deleteIfExists(authorization, "/api/device/" + request.tbDeviceId()));
        deprovisioningStep("delete asset", () -> deleteIfExists(authorization, "/api/asset/" + request.tbAssetId()));
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

    private <T> T provisioningStep(String stepName, Supplier<T> action) {
        try {
            return action.get();
        } catch (HttpClientErrorException.Unauthorized error) {
            log.warn("ThingsBoard provisioning failed during {}: HTTP 401.", stepName);
            if (properties.getProvisioningAuthMode() == ProvisioningAuthMode.JWT) {
                throw new ThingsBoardProvisioningException("ThingsBoard JWT token je istekao ili nije valjan. Obnovite THINGSBOARD_JWT_TOKEN.", error);
            }
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning autentifikacija nije uspjela kod koraka: " + stepName + ".", error);
        } catch (HttpClientErrorException.Forbidden error) {
            log.warn("ThingsBoard provisioning failed during {}: HTTP 403.", stepName);
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nema dozvolu za korak: " + stepName + ".", error);
        }
    }

    private void deprovisioningStep(String stepName, Runnable action) {
        try {
            action.run();
        } catch (HttpClientErrorException.Unauthorized error) {
            log.warn("ThingsBoard deprovisioning failed during {}: HTTP 401.", stepName);
            if (properties.getProvisioningAuthMode() == ProvisioningAuthMode.JWT) {
                throw new ThingsBoardProvisioningException("ThingsBoard JWT token je istekao ili nije valjan. Obnovite THINGSBOARD_JWT_TOKEN.", error);
            }
            throw new ThingsBoardProvisioningException("ThingsBoard deprovisioning autentifikacija nije uspjela kod koraka: " + stepName + ".", error);
        } catch (HttpClientErrorException.Forbidden error) {
            log.warn("ThingsBoard deprovisioning failed during {}: HTTP 403.", stepName);
            throw new ThingsBoardProvisioningException("ThingsBoard deprovisioning nema dozvolu za korak: " + stepName + ".", error);
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard deprovisioning failed during {}: {}", stepName, error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard deprovisioning nije uspio kod koraka: " + stepName + ".", error);
        }
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

    private String fetchDeviceAccessToken(String authorization, String deviceId) {
        Map<String, Object> response = restClient.get()
                .uri(properties.getHost() + "/api/device/" + deviceId + "/credentials")
                .header(AUTH_HEADER, authorization)
                .retrieve()
                .body(MAP_RESPONSE);

        String credentialsType = stringValue(response, "credentialsType");
        String credentialsId = stringValue(response, "credentialsId");
        String credentialsValue = stringValue(response, "credentialsValue");
        String token = !credentialsId.isBlank() ? credentialsId : credentialsValue;
        if (!"ACCESS_TOKEN".equals(credentialsType) || token.isBlank()) {
            throw new IllegalArgumentException("ThingsBoard device credentials ne sadrze access token.");
        }

        return token;
    }

    private void verifyDeviceExists(String authorization, String deviceId) {
        restClient.get()
                .uri(properties.getHost() + "/api/device/" + deviceId)
                .header(AUTH_HEADER, authorization)
                .retrieve()
                .toBodilessEntity();
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

    private void deleteIfExists(String authorization, String pathAndQuery) {
        try {
            restClient.delete()
                    .uri(properties.getHost() + pathAndQuery)
                    .header(AUTH_HEADER, authorization)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            log.debug("ThingsBoard entity already absent during hard delete.");
        }
    }

    private void deleteRelationBestEffort(String authorization, VirtualRoomDeprovisioningRequest request) {
        try {
            deleteIfExists(
                    authorization,
                    "/api/relation?fromId=" + request.tbAssetId()
                            + "&fromType=ASSET"
                            + "&relationType=Contains"
                            + "&relationTypeGroup=COMMON"
                            + "&toId=" + request.tbDeviceId()
                            + "&toType=DEVICE"
            );
        } catch (HttpServerErrorException error) {
            log.warn("ThingsBoard relation delete returned HTTP {} for room {}; continuing with hard delete of device and asset.",
                    error.getStatusCode().value(),
                    request.roomId());
        }
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

    private static String stringValue(Map<String, Object> map, String key) {
        Object value = value(map, key);
        return value == null ? "" : value.toString();
    }

    private static boolean isLinkableAssetId(String tbAssetId) {
        return tbAssetId != null
                && !tbAssetId.isBlank()
                && !PENDING_THINGSBOARD_ID.equals(tbAssetId)
                && !tbAssetId.startsWith(MOCK_THINGSBOARD_ASSET_PREFIX);
    }
}
