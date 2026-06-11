package com.windowsense.integration.thingsboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windowsense.exception.ThingsBoardProvisioningException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.config.WindowSenseProperties.ProvisioningAuthMode;
import com.windowsense.config.WindowSenseProperties.ThingsBoardDeleteMode;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Supplier;

@Service
@ConditionalOnProperty(prefix = "windowsense.things-board", name = "provisioning-enabled", havingValue = "true")
public class ThingsBoardRestProvisioningService implements ThingsBoardProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ThingsBoardRestProvisioningService.class);

    private static final String ASSET_TYPE = "WindowSense Room";
    private static final String VIRTUAL_DEVICE_TYPE = "WindowSense Virtual Window";
    private static final String PHYSICAL_DEVICE_TYPE = "WindowSense Physical ESP32";
    private static final String WINDOW_PROFILE_NAME = "WindowSense Window Profile";
    private static final String BLINDS_PROFILE_NAME = "WindowSense Blinds Profile";
    private static final String COMBINED_PROFILE_NAME = "WindowSense Combined Profile";
    private static final String AUTOMATION_RULE_CHAIN_NAME = "TestProzorChain";
    private static final String AUTOMATION_RULE_CHAIN_RESOURCE = "thingsboard/rule-chains/testprozorchain.json";
    private static final String PENDING_THINGSBOARD_ID = "pending-thingsboard-provisioning";
    private static final String MOCK_THINGSBOARD_ASSET_PREFIX = "tb-asset-";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE = new ParameterizedTypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final WindowSenseProperties.ThingsBoard properties;
    private final RestClient restClient;
    private final ThingsBoardAuthClient authClient;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ThingsBoardRestProvisioningService(WindowSenseProperties properties, RestClient.Builder builder) {
        this(properties, builder, new ThingsBoardAuthClient(properties, builder));
    }

    @Autowired
    public ThingsBoardRestProvisioningService(
            WindowSenseProperties properties,
            RestClient.Builder builder,
            ThingsBoardAuthClient authClient
    ) {
        this.properties = properties.getThingsBoard();
        this.restClient = builder.build();
        this.authClient = authClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncConfiguredRuleChainsOnStartup() {
        if (!properties.isProvisioningReady() || !properties.getRuleChains().isAutoSync()) {
            return;
        }

        try {
            String authorization = authorizationHeader();
            ensureRuleChain(authorization, AUTOMATION_RULE_CHAIN_NAME, AUTOMATION_RULE_CHAIN_RESOURCE);
        } catch (RestClientException | IllegalArgumentException | ThingsBoardProvisioningException error) {
            log.warn("ThingsBoard rule chain startup sync nije uspio: {}", error.getMessage());
        }
    }

    @Override
    public ProvisionedRoomAsset provisionRoomAsset(RoomAssetProvisioningRequest request) {
        if (!properties.isProvisioningReady()) {
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nije ispravno konfiguriran.");
        }

        try {
            String authorization = authorizationHeader();
            String suffix = request.roomId().toString().substring(0, 8);
            String tbAssetName = "WindowSense Room - " + request.roomName() + " [" + suffix + "]";
            String assetId = provisioningStep("create room asset", () -> createAsset(authorization, tbAssetName, request.roomName()));
            provisioningStep("save room asset attributes", () -> {
                saveRoomAssetAttributes(authorization, assetId, request.roomId(), request.roomName(), request.appUserId(), request.auth0Sub());
                return null;
            });
            return new ProvisionedRoomAsset(assetId);
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard room asset provisioning failed: {}", error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning sobe nije uspio.", error);
        }
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

            String assetId = isLinkableAssetId(request.tbAssetId())
                    ? request.tbAssetId()
                    : provisioningStep("create asset", () -> createAsset(authorization, tbAssetName, request.roomName()));
            String profileId = provisioningStep("sync automation device profile/rule chain", () -> deviceProfileId(authorization, request.capabilities()));
            String deviceId = provisioningStep("create device", () -> createDevice(authorization, tbDeviceName, request.deviceName(), VIRTUAL_DEVICE_TYPE, profileId));
            String deviceAccessToken = provisioningStep("fetch device credentials", () -> fetchDeviceAccessToken(authorization, deviceId));
            provisioningStep("create relation", () -> {
                createRelation(authorization, assetId, deviceId);
                return null;
            });
            provisioningStep("save room asset attributes", () -> {
                saveRoomAssetAttributes(authorization, assetId, request.roomId(), request.roomName(), request.appUserId(), request.auth0Sub());
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
    public ProvisionedPhysicalDevice provisionPhysicalEspDevice(PhysicalEspProvisioningRequest request) {
        if (!properties.isProvisioningReady()) {
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nije ispravno konfiguriran.");
        }

        try {
            String authorization = authorizationHeader();
            String suffix = request.roomId().toString().substring(0, 8);
            String tbDeviceName = request.deviceName() + " [" + request.serialNumber() + "][" + suffix + "]";
            String accessToken = generateDeviceAccessToken();

            String profileId = provisioningStep("sync physical automation device profile/rule chain", () -> deviceProfileId(authorization, request.capabilities()));
            String deviceId = provisioningStep("create physical device", () -> createDevice(
                    authorization,
                    tbDeviceName,
                    request.deviceName(),
                    PHYSICAL_DEVICE_TYPE,
                    profileId
            ));
            provisioningStep("set physical device credentials", () -> {
                setDeviceAccessToken(authorization, deviceId, accessToken);
                return null;
            });
            if (isLinkableAssetId(request.tbAssetId())) {
                provisioningStep("create physical device relation", () -> {
                    createRelation(authorization, request.tbAssetId(), deviceId);
                    return null;
                });
            }
            provisioningStep("save physical device attributes", () -> {
                savePhysicalDeviceAttributes(authorization, deviceId, request);
                return null;
            });

            return new ProvisionedPhysicalDevice(deviceId, accessToken);
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard physical ESP provisioning failed: {}", error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning fizickog ESP uredjaja nije uspio.", error);
        }
    }

    @Override
    public RegisteredPhysicalEspDevice registerPhysicalEspDeviceWithToken(PhysicalEspTokenRegistrationRequest request) {
        if (!properties.isProvisioningReady()) {
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nije ispravno konfiguriran.");
        }

        try {
            String authorization = authorizationHeader();
            String profileId = provisioningStep("sync physical token automation device profile/rule chain", () -> deviceProfileId(authorization, request.capabilities()));
            String deviceId = provisioningStep("create physical token device", () -> createDevice(
                    authorization,
                    request.deviceName() + " [" + request.serialNumber() + "]",
                    request.deviceName(),
                    PHYSICAL_DEVICE_TYPE,
                    profileId
            ));
            provisioningStep("set physical token device credentials", () -> {
                setDeviceAccessToken(authorization, deviceId, request.thingsBoardAccessToken());
                return null;
            });
            provisioningStep("save physical token device attributes", () -> {
                savePhysicalDeviceRegistrationAttributes(authorization, deviceId, request);
                return null;
            });
            return new RegisteredPhysicalEspDevice(deviceId);
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard physical ESP token registration failed: {}", error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard registracija fizickog ESP uredjaja nije uspjela.", error);
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
            provisioningStep("assign physical device automation profile", () -> {
                assignDeviceProfileIfConfigured(authorization, request.tbDeviceId(), request.capabilities());
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
    public void syncRoomAutomationAttributes(RoomAutomationAttributesRequest request) {
        if (!properties.isProvisioningReady()) {
            return;
        }

        try {
            String authorization = authorizationHeader();
            provisioningStep("sync room automation attributes", () -> {
                saveAttributes(
                        authorization,
                        "DEVICE",
                        request.tbDeviceId(),
                        "SHARED_SCOPE",
                        Map.of(
                                "roomId", request.roomId().toString(),
                                "roomName", request.roomName(),
                                "rainThreshold", request.rainThreshold(),
                                "desiredRainProbability", request.rainThreshold()
                        )
                );
                return null;
            });
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard automation attribute sync failed for room {} device {}: {}",
                    request.roomId(),
                    request.tbDeviceId(),
                    error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard sinkronizacija automation atributa nije uspjela.", error);
        }
    }

    @Override
    public void syncDeviceSharedAttributes(String tbDeviceId, Map<String, Object> attributes) {
        if (!properties.isProvisioningReady() || tbDeviceId == null || tbDeviceId.isBlank() || attributes == null || attributes.isEmpty()) {
            return;
        }

        try {
            String authorization = authorizationHeader();
            provisioningStep("sync device shared attributes", () -> {
                saveAttributes(
                        authorization,
                        "DEVICE",
                        tbDeviceId,
                        "SHARED_SCOPE",
                        attributes
                );
                return null;
            });
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard shared attribute sync failed for device {}: {}",
                    tbDeviceId,
                    error.getClass().getSimpleName());
        }
    }

    @Override
    public String fetchDeviceAccessToken(String tbDeviceId) {
        if (!properties.isProvisioningReady()) {
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nije ispravno konfiguriran.");
        }
        try {
            String authorization = authorizationHeader();
            return fetchDeviceAccessToken(authorization, tbDeviceId);
        } catch (RestClientException | IllegalArgumentException error) {
            throw new ThingsBoardProvisioningException("Neuspjelo dohvacanje ThingsBoard access tokena.", error);
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

    @Override
    public void deprovisionRoomDevice(RoomDeviceDeprovisioningRequest request) {
        if (!properties.isProvisioningReady()) {
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nije ispravno konfiguriran.");
        }

        try {
            String authorization = authorizationHeader();
            if (properties.getDeleteMode() == ThingsBoardDeleteMode.HARD) {
                hardDeleteRoomDevice(authorization, request);
                return;
            }

            softDeleteRoomDevice(authorization, request);
        } catch (HttpClientErrorException.Unauthorized error) {
            log.warn("ThingsBoard device deprovisioning failed with HTTP 401.");
            if (properties.getProvisioningAuthMode() == ProvisioningAuthMode.JWT) {
                throw new ThingsBoardProvisioningException("ThingsBoard JWT token je istekao ili nije valjan. Obnovite THINGSBOARD_JWT_TOKEN.", error);
            }
            throw new ThingsBoardProvisioningException("ThingsBoard deprovisioning uredjaja autentifikacija nije uspjela.", error);
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard device deprovisioning failed for room {} device {}: {}",
                    request.roomId(),
                    request.tbDeviceId(),
                    error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard deprovisioning uredjaja nije uspio.", error);
        }
    }

    @Override
    public void deprovisionRoomAsset(RoomAssetDeprovisioningRequest request) {
        if (!properties.isProvisioningReady()) {
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nije ispravno konfiguriran.");
        }

        try {
            String authorization = authorizationHeader();
            if (properties.getDeleteMode() == ThingsBoardDeleteMode.HARD) {
                deprovisioningStep("delete room asset", () -> deleteIfExists(authorization, "/api/asset/" + request.tbAssetId()));
                return;
            }

            deprovisioningStep("mark room asset deleted", () -> saveAttributes(
                    authorization,
                    "ASSET",
                    request.tbAssetId(),
                    "SERVER_SCOPE",
                    Map.of("active", false, "deletedFromApp", true)
            ));
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard room asset deprovisioning failed for room {}: {}", request.roomId(), error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard deprovisioning sobe nije uspio.", error);
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

    private void softDeleteRoomDevice(String authorization, RoomDeviceDeprovisioningRequest request) {
        Map<String, Object> attributes = Map.of(
                "active", false,
                "deletedFromApp", true
        );
        deprovisioningStep("mark room device deleted in server scope", () -> saveAttributes(authorization, "DEVICE", request.tbDeviceId(), "SERVER_SCOPE", attributes));
        deprovisioningStep("mark room device deleted in shared scope", () -> saveAttributes(authorization, "DEVICE", request.tbDeviceId(), "SHARED_SCOPE", attributes));
    }

    private void hardDeleteRoom(String authorization, VirtualRoomDeprovisioningRequest request) {
        deleteRelationBestEffort(authorization, request);
        deprovisioningStep("delete device", () -> deleteIfExists(authorization, "/api/device/" + request.tbDeviceId()));
        deprovisioningStep("delete asset", () -> deleteIfExists(authorization, "/api/asset/" + request.tbAssetId()));
    }

    private void hardDeleteRoomDevice(String authorization, RoomDeviceDeprovisioningRequest request) {
        deleteRelationBestEffort(authorization, request.roomId(), request.tbAssetId(), request.tbDeviceId());
        deprovisioningStep("delete room device", () -> deleteIfExists(authorization, "/api/device/" + request.tbDeviceId()));
    }

    private String authorizationHeader() {
        return authClient.authorizationHeader();
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
            String responsePreview = responsePreview(error);
            log.warn("ThingsBoard provisioning failed during {}: HTTP 403. Response: {}", stepName, responsePreview);
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nema dozvolu za korak: " + stepName + ". Odgovor: " + responsePreview, error);
        } catch (HttpClientErrorException.BadRequest error) {
            String responsePreview = responsePreview(error);
            log.warn("ThingsBoard provisioning failed during {}: HTTP 400. Response: {}", stepName, responsePreview);
            throw new ThingsBoardProvisioningException("ThingsBoard nije prihvatio provisioning zahtjev kod koraka: " + stepName + ". Odgovor: " + responsePreview, error);
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard provisioning failed during {}: {}", stepName, error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard provisioning nije uspio kod koraka: " + stepName + ".", error);
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
            String responsePreview = responsePreview(error);
            log.warn("ThingsBoard deprovisioning failed during {}: HTTP 403. Response: {}", stepName, responsePreview);
            throw new ThingsBoardProvisioningException("ThingsBoard deprovisioning nema dozvolu za korak: " + stepName + ". Odgovor: " + responsePreview, error);
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard deprovisioning failed during {}: {}", stepName, error.getClass().getSimpleName());
            throw new ThingsBoardProvisioningException("ThingsBoard deprovisioning nije uspio kod koraka: " + stepName + ".", error);
        }
    }

    private String createAsset(String authorization, String name, String label) {
        Map<String, Object> response = restClient.post()
                .uri(properties.getHost() + "/api/asset")
                .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
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

    private String createDevice(String authorization, String name, String label, String type) {
        return createDevice(authorization, name, label, type, null);
    }

    private String createDevice(String authorization, String name, String label, String type, String deviceProfileId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("label", label);
        body.put("type", type);
        if (deviceProfileId != null && !deviceProfileId.isBlank()) {
            body.put("deviceProfileId", Map.of(
                    "entityType", "DEVICE_PROFILE",
                    "id", deviceProfileId
            ));
        }

        Map<String, Object> response = restClient.post()
                .uri(properties.getHost() + "/api/device")
                .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP_RESPONSE);

        return entityId(response, "Device");
    }

    private String deviceProfileId(String authorization, List<String> capabilities) {
        if (!properties.getRuleChains().isAutoSync()) {
            return null;
        }
        AutomationTarget target = automationTarget(capabilities);
        if (target == AutomationTarget.NONE) {
            return null;
        }
        String ruleChainId = ensureRuleChain(authorization, AUTOMATION_RULE_CHAIN_NAME, AUTOMATION_RULE_CHAIN_RESOURCE);
        return switch (target) {
            case WINDOW -> ensureDeviceProfile(authorization, WINDOW_PROFILE_NAME, ruleChainId);
            case BLINDS -> ensureDeviceProfile(authorization, BLINDS_PROFILE_NAME, ruleChainId);
            case COMBINED -> ensureDeviceProfile(authorization, COMBINED_PROFILE_NAME, ruleChainId);
            case NONE -> null;
        };
    }

    private void assignDeviceProfileIfConfigured(String authorization, String deviceId, List<String> capabilities) {
        String profileId = deviceProfileId(authorization, capabilities);
        if (profileId == null || profileId.isBlank()) {
            return;
        }
        Map<String, Object> device = restClient.get()
                .uri(properties.getHost() + "/api/device/" + deviceId)
                .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                .retrieve()
                .body(MAP_RESPONSE);
        if (device == null) {
            return;
        }
        device.put("deviceProfileId", Map.of(
                "entityType", "DEVICE_PROFILE",
                "id", profileId
        ));
        restClient.post()
                .uri(properties.getHost() + "/api/device")
                .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(device)
                .retrieve()
                .toBodilessEntity();
    }

    private String ensureRuleChain(String authorization, String name, String resourcePath) {
        String existing = provisioningStep(
                "find rule chain " + name,
                () -> findEntityIdByName(authorization, "/api/ruleChains?pageSize=100", name)
        );
        Map<String, Object> export = readRuleChain(resourcePath);
        if (!existing.isBlank()) {
            provisioningStep("sync rule chain metadata " + name, () -> {
                saveExportedRuleChainMetadata(authorization, existing, export);
                return null;
            });
            return existing;
        }
        Map<String, Object> ruleChain = exportRuleChain(export, name);
        Map<String, Object> response = provisioningStep("create rule chain " + name, () -> restClient.post()
                        .uri(properties.getHost() + "/api/ruleChain")
                        .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ruleChain)
                        .retrieve()
                        .body(MAP_RESPONSE));
        String ruleChainId = entityId(response, "Rule Chain");
        provisioningStep("save rule chain metadata " + name, () -> {
            saveExportedRuleChainMetadata(authorization, ruleChainId, export);
            return null;
        });
        return ruleChainId;
    }

    private void saveExportedRuleChainMetadata(String authorization, String ruleChainId, Map<String, Object> export) {
        Map<String, Object> metadata = exportMetadata(export);
        metadata.put("ruleChainId", Map.of("entityType", "RULE_CHAIN", "id", ruleChainId));
        restClient.post()
                .uri(properties.getHost() + "/api/ruleChain/metadata")
                .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(metadata)
                .retrieve()
                .toBodilessEntity();
    }

    private String ensureDeviceProfile(String authorization, String profileName, String ruleChainId) {
        String existing = provisioningStep(
                "find device profile " + profileName,
                () -> findEntityIdByName(
                        authorization,
                        "/api/deviceProfiles?pageSize=100",
                        profileName
                )
        );
        if (!existing.isBlank()) {
            provisioningStep("update device profile " + profileName + " rule chain", () -> {
                Map<String, Object> profile = restClient.get()
                        .uri(properties.getHost() + "/api/deviceProfile/" + existing)
                        .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                        .retrieve()
                        .body(MAP_RESPONSE);
                if (profile != null) {
                    profile.put("defaultRuleChainId", Map.of(
                            "entityType", "RULE_CHAIN",
                            "id", ruleChainId
                    ));
                    restClient.post()
                            .uri(properties.getHost() + "/api/deviceProfile")
                            .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(profile)
                            .retrieve()
                            .toBodilessEntity();
                }
                return null;
            });
            return existing;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", profileName);
        body.put("type", "DEFAULT");
        body.put("transportType", "DEFAULT");
        body.put("default", false);
        body.put("defaultRuleChainId", Map.of(
                "entityType", "RULE_CHAIN",
                "id", ruleChainId
        ));
        body.put("profileData", Map.of(
                "configuration", Map.of("type", "DEFAULT"),
                "transportConfiguration", Map.of("type", "DEFAULT")
        ));
        Map<String, Object> response = provisioningStep("create device profile " + profileName, () -> restClient.post()
                        .uri(properties.getHost() + "/api/deviceProfile")
                        .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(MAP_RESPONSE));
        return entityId(response, "Device Profile");
    }

    private String findEntityIdByName(String authorization, String pathAndQueryWithoutPage, String name) {
        for (int page = 0; page < 20; page++) {
            Map<String, Object> response = restClient.get()
                    .uri(properties.getHost() + pathAndQueryWithoutPage + "&page=" + page)
                    .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                    .retrieve()
                    .body(MAP_RESPONSE);
            for (Map<String, Object> item : dataItems(response)) {
                if (name.equals(stringValue(item, "name"))) {
                    return entityId(item, name);
                }
            }
            if (!hasNext(response)) {
                return "";
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dataItems(Map<String, Object> response) {
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        normalized.put(entry.getKey().toString(), entry.getValue());
                    }
                }
                items.add(normalized);
            }
        }
        return items;
    }

    private boolean hasNext(Map<String, Object> response) {
        Object hasNext = response == null ? null : response.get("hasNext");
        return Boolean.TRUE.equals(hasNext);
    }

    private Map<String, Object> readRuleChain(String resourcePath) {
        try (InputStream stream = new ClassPathResource(resourcePath).getInputStream()) {
            return objectMapper.readValue(stream, MAP_TYPE);
        } catch (IOException error) {
            throw new IllegalArgumentException("ThingsBoard rule chain JSON nije pronadjen: " + resourcePath, error);
        }
    }

    private Map<String, Object> exportRuleChain(Map<String, Object> export, String fallbackName) {
        Object rawRuleChain = export == null ? null : export.get("ruleChain");
        if (!(rawRuleChain instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException("ThingsBoard rule chain export ne sadrzi ruleChain objekt.");
        }
        Map<String, Object> ruleChain = normalizedMap(source);
        ruleChain.remove("id");
        ruleChain.remove("tenantId");
        ruleChain.remove("customerId");
        ruleChain.putIfAbsent("name", fallbackName);
        return ruleChain;
    }

    private Map<String, Object> exportMetadata(Map<String, Object> export) {
        Object rawMetadata = export == null ? null : export.get("metadata");
        if (!(rawMetadata instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException("ThingsBoard rule chain export ne sadrzi metadata objekt.");
        }
        Map<String, Object> metadata = normalizedMap(source);
        metadata.remove("id");
        metadata.remove("ruleChainId");
        metadata.remove("version");
        return metadata;
    }

    private Map<String, Object> normalizedMap(Map<?, ?> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return normalized;
    }

    private AutomationTarget automationTarget(List<String> capabilities) {
        boolean window = hasCapability(capabilities, "WINDOW_CONTROL", "window");
        boolean blinds = hasCapability(capabilities, "BLINDS_CONTROL", "blinds");
        if (window && blinds) {
            return AutomationTarget.COMBINED;
        }
        if (window) {
            return AutomationTarget.WINDOW;
        }
        if (blinds) {
            return AutomationTarget.BLINDS;
        }
        return AutomationTarget.NONE;
    }

    private boolean hasCapability(List<String> capabilities, String enumName, String label) {
        if (capabilities == null) {
            return false;
        }
        for (String capability : capabilities) {
            if (capability == null) {
                continue;
            }
            String normalized = capability.trim();
            if (enumName.equalsIgnoreCase(normalized) || label.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    private enum AutomationTarget {
        WINDOW,
        BLINDS,
        COMBINED,
        NONE
    }

    private void setDeviceAccessToken(String authorization, String deviceId, String accessToken) {
        Map<String, Object> credentials = new LinkedHashMap<>(fetchDeviceCredentials(authorization, deviceId));
        credentials.put("deviceId", Map.of(
                "entityType", "DEVICE",
                "id", deviceId
        ));
        credentials.put("credentialsType", "ACCESS_TOKEN");
        credentials.put("credentialsId", accessToken);
        credentials.remove("credentialsValue");

        restClient.post()
                .uri(properties.getHost() + "/api/device/credentials")
                .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(credentials)
                .retrieve()
                .toBodilessEntity();
    }

    private String fetchDeviceAccessToken(String authorization, String deviceId) {
        Map<String, Object> response = fetchDeviceCredentials(authorization, deviceId);
        String credentialsType = stringValue(response, "credentialsType");
        String credentialsId = stringValue(response, "credentialsId");
        String credentialsValue = stringValue(response, "credentialsValue");
        String token = !credentialsId.isBlank() ? credentialsId : credentialsValue;
        if (!"ACCESS_TOKEN".equals(credentialsType) || token.isBlank()) {
            throw new IllegalArgumentException("ThingsBoard device credentials ne sadrze access token.");
        }

        return token;
    }

    private Map<String, Object> fetchDeviceCredentials(String authorization, String deviceId) {
        return restClient.get()
                .uri(properties.getHost() + "/api/device/" + deviceId + "/credentials")
                .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                .retrieve()
                .body(MAP_RESPONSE);
    }

    private void verifyDeviceExists(String authorization, String deviceId) {
        restClient.get()
                .uri(properties.getHost() + "/api/device/" + deviceId)
                .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                .retrieve()
                .toBodilessEntity();
    }

    private void createRelation(String authorization, String assetId, String deviceId) {
        restClient.post()
                .uri(properties.getHost() + "/api/relation")
                .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
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

    private static String responsePreview(HttpClientErrorException error) {
        String body = error.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }

    private void saveDeviceAttributes(String authorization, String deviceId, VirtualRoomProvisioningRequest request) {
        Map<String, Object> serverAttributes = new LinkedHashMap<>();
        serverAttributes.put("isVirtual", true);
        serverAttributes.put("roomId", request.roomId().toString());
        serverAttributes.put("roomName", request.roomName());
        serverAttributes.put("appUserId", request.appUserId().toString());
        serverAttributes.put("auth0Sub", request.auth0Sub());
        serverAttributes.put("deviceType", "VIRTUAL");
        serverAttributes.put("active", true);
        serverAttributes.put("deletedFromApp", false);

        Map<String, Object> sharedAttributes = new LinkedHashMap<>();
        sharedAttributes.put("isVirtual", true);
        sharedAttributes.put("roomId", request.roomId().toString());
        sharedAttributes.put("roomName", request.roomName());
        sharedAttributes.put("deviceType", "VIRTUAL");
        sharedAttributes.put("deletedFromApp", false);
        sharedAttributes.put("day", 1);
        sharedAttributes.put("desiredAngle", 70);
        sharedAttributes.put("desiredAngleDay", 90);
        sharedAttributes.put("desiredAngleNight", 0);
        sharedAttributes.put("desiredAngleRain", 15);
        sharedAttributes.put("desiredRainProbability", 70);
        sharedAttributes.put("manualMode", false);
        sharedAttributes.put("rainProbability", 77);

        saveAttributes(authorization, "DEVICE", deviceId, "SERVER_SCOPE", serverAttributes);
        saveAttributes(authorization, "DEVICE", deviceId, "SHARED_SCOPE", sharedAttributes);
    }

    private void saveRoomAssetAttributes(
            String authorization,
            String assetId,
            java.util.UUID roomId,
            String roomName,
            java.util.UUID appUserId,
            String auth0Sub
    ) {
        Map<String, Object> attributes = Map.of(
                "roomId", roomId.toString(),
                "roomName", roomName,
                "appUserId", appUserId.toString(),
                "auth0Sub", auth0Sub,
                "assetType", "ROOM",
                "active", true,
                "deletedFromApp", false
        );

        saveAttributes(authorization, "ASSET", assetId, "SERVER_SCOPE", attributes);
    }

    private void savePhysicalDeviceAttributes(String authorization, String deviceId, PhysicalEspProvisioningRequest request) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("isVirtual", false);
        attributes.put("roomId", request.roomId().toString());
        attributes.put("roomName", request.roomName());
        attributes.put("appUserId", request.appUserId().toString());
        attributes.put("auth0Sub", request.auth0Sub());
        attributes.put("deviceType", "PHYSICAL");
        attributes.put("serialNumber", request.serialNumber());
        attributes.put("hardwareId", blankToEmpty(request.hardwareId()));
        attributes.put("firmwareVersion", blankToEmpty(request.firmwareVersion()));
        attributes.put("capabilities", request.capabilities());
        attributes.put("active", true);
        attributes.put("deletedFromApp", false);

        saveAttributes(authorization, "DEVICE", deviceId, "SERVER_SCOPE", attributes);
        saveAttributes(authorization, "DEVICE", deviceId, "SHARED_SCOPE", attributes);
    }

    private void savePhysicalDeviceRegistrationAttributes(String authorization, String deviceId, PhysicalEspTokenRegistrationRequest request) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("isVirtual", false);
        attributes.put("deviceType", "PHYSICAL");
        attributes.put("serialNumber", request.serialNumber());
        attributes.put("hardwareId", blankToEmpty(request.hardwareId()));
        attributes.put("firmwareVersion", blankToEmpty(request.firmwareVersion()));
        attributes.put("capabilities", request.capabilities());
        attributes.put("active", true);
        attributes.put("registeredFromApp", true);
        attributes.put("deletedFromApp", false);

        saveAttributes(authorization, "DEVICE", deviceId, "SERVER_SCOPE", attributes);
        saveAttributes(authorization, "DEVICE", deviceId, "SHARED_SCOPE", attributes);
    }

    private void saveAttributes(String authorization, String entityType, String entityId, String scope, Map<String, Object> attributes) {
        restClient.post()
                .uri(properties.getHost() + "/api/plugins/telemetry/" + entityType + "/" + entityId + "/attributes/" + scope)
                .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(attributes)
                .retrieve()
                .toBodilessEntity();
    }

    private void deleteIfExists(String authorization, String pathAndQuery) {
        try {
            restClient.delete()
                    .uri(properties.getHost() + pathAndQuery)
                    .header(ThingsBoardAuthClient.AUTH_HEADER, authorization)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            log.debug("ThingsBoard entity already absent during hard delete.");
        }
    }

    private void deleteRelationBestEffort(String authorization, VirtualRoomDeprovisioningRequest request) {
        deleteRelationBestEffort(authorization, request.roomId(), request.tbAssetId(), request.tbDeviceId());
    }

    private void deleteRelationBestEffort(String authorization, java.util.UUID roomId, String tbAssetId, String tbDeviceId) {
        if (!isLinkableAssetId(tbAssetId)) {
            return;
        }
        try {
            deleteIfExists(
                    authorization,
                    "/api/relation?fromId=" + tbAssetId
                            + "&fromType=ASSET"
                            + "&relationType=Contains"
                            + "&relationTypeGroup=COMMON"
                            + "&toId=" + tbDeviceId
                            + "&toType=DEVICE"
            );
        } catch (HttpServerErrorException error) {
            log.warn("ThingsBoard relation delete returned HTTP {} for room {}; continuing with hard delete of device and asset.",
                    error.getStatusCode().value(),
                    roomId);
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

    private String generateDeviceAccessToken() {
        byte[] token = new byte[24];
        secureRandom.nextBytes(token);
        return "ws_" + Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isLinkableAssetId(String tbAssetId) {
        return tbAssetId != null
                && !tbAssetId.isBlank()
                && !PENDING_THINGSBOARD_ID.equals(tbAssetId)
                && !tbAssetId.startsWith(MOCK_THINGSBOARD_ASSET_PREFIX);
    }
}
