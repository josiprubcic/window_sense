package com.windowsense.integration.thingsboard;

import com.windowsense.exception.ThingsBoardProvisioningException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.config.WindowSenseProperties.ProvisioningAuthMode;
import com.windowsense.config.WindowSenseProperties.ThingsBoardDeleteMode;
import com.windowsense.integration.thingsboard.ExistingPhysicalDeviceLinkRequest;
import com.windowsense.integration.thingsboard.PhysicalEspProvisioningRequest;
import com.windowsense.integration.thingsboard.PhysicalEspTokenRegistrationRequest;
import com.windowsense.integration.thingsboard.RoomAssetProvisioningRequest;
import com.windowsense.integration.thingsboard.RoomDeviceDeprovisioningRequest;
import com.windowsense.integration.thingsboard.ThingsBoardRestProvisioningService;
import com.windowsense.integration.thingsboard.VirtualRoomDeprovisioningRequest;
import com.windowsense.integration.thingsboard.VirtualRoomProvisioningRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ThingsBoardRestProvisioningServiceTest {

    private static final String HOST = "https://thingsboard.example";
    private static final UUID ROOM_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void provisionsRoomAssetWithoutDevice() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");

        expectPost(context.server, HOST + "/api/asset", "ApiKey provisioning-api-key", """
                {
                  "id": {
                    "entityType": "ASSET",
                    "id": "asset-id"
                  }
                }
                """);
        expectPost(context.server, HOST + "/api/plugins/telemetry/ASSET/asset-id/attributes/SERVER_SCOPE", "ApiKey provisioning-api-key", "");

        var provisioned = context.service.provisionRoomAsset(new RoomAssetProvisioningRequest(
                ROOM_ID,
                "Kuhinja",
                USER_ID,
                "auth0|window-user"
        ));

        assertThat(provisioned.tbAssetId()).isEqualTo("asset-id");
        context.server.verify();
    }

    @Test
    void passwordAuthModeUsesLoginToken() {
        TestContext context = context(ProvisioningAuthMode.PASSWORD);
        context.properties.getThingsBoard().setUsername("tenant@example.com");
        context.properties.getThingsBoard().setPassword("secret-password");

        expectLogin(context.server);
        expectProvisioningCalls(context.server, "Bearer jwt-from-login");

        var provisioned = context.service.provisionVirtualRoomDevice(request());

        assertThat(provisioned.tbDeviceAccessToken()).isEqualTo("device-access-token");
        context.server.verify();
    }

    @Test
    void jwtAuthModeUsesBearerHeaderWithoutLogin() {
        TestContext context = context(ProvisioningAuthMode.JWT);
        context.properties.getThingsBoard().setJwtToken("jwt-provisioning-token");

        expectProvisioningCalls(context.server, "Bearer jwt-provisioning-token");

        var provisioned = context.service.provisionVirtualRoomDevice(request());

        assertThat(provisioned.tbDeviceAccessToken()).isEqualTo("device-access-token");
        context.server.verify();
    }

    @Test
    void apiKeyAuthModeUsesApiKeyHeaderWithoutLogin() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");

        expectProvisioningCalls(context.server, "ApiKey provisioning-api-key");

        var provisioned = context.service.provisionVirtualRoomDevice(request());

        assertThat(provisioned.tbDeviceAccessToken()).isEqualTo("device-access-token");
        context.server.verify();
    }

    @Test
    void syncRoomAutomationAttributesWritesSharedThresholdsUsedByRuleChain() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");

        context.server.expect(once(), requestTo(HOST + "/api/plugins/telemetry/DEVICE/device-id/attributes/SHARED_SCOPE"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Authorization", "ApiKey provisioning-api-key"))
                .andExpect(content().json("""
                        {
                          "roomId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                          "roomName": "Kuhinja",
                          "rainThreshold": 55.0,
                          "desiredRainProbability": 55.0
                        }
                        """, true))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        context.service.syncRoomAutomationAttributes(new RoomAutomationAttributesRequest(
                ROOM_ID,
                "Kuhinja",
                "device-id",
                55.0
        ));

        context.server.verify();
    }

    @Test
    void syncDeviceSharedAttributesWritesRuntimeValuesUsedByRuleChain() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");

        context.server.expect(once(), requestTo(HOST + "/api/plugins/telemetry/DEVICE/device-id/attributes/SHARED_SCOPE"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Authorization", "ApiKey provisioning-api-key"))
                .andExpect(content().json("""
                        {
                          "rainProbability": 31.0
                        }
                        """, true))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        context.service.syncDeviceSharedAttributes("device-id", Map.of("rainProbability", 31.0));

        context.server.verify();
    }


    @Test
    void autoSyncUsesSharedRuleChainAndExistingDeviceProfileByExactName() {
        TestContext context = context(ProvisioningAuthMode.JWT);
        context.properties.getThingsBoard().setJwtToken("jwt-provisioning-token");
        context.properties.getThingsBoard().getRuleChains().setAutoSync(true);

        expectPost(context.server, HOST + "/api/asset", "Bearer jwt-provisioning-token", """
                {
                  "id": {
                    "entityType": "ASSET",
                    "id": "asset-id"
                  }
                }
                """);
        expectGet(context.server, HOST + "/api/ruleChains?pageSize=100&page=0", "Bearer jwt-provisioning-token", """
                {
                  "data": [
                    {
                      "id": {
                        "entityType": "RULE_CHAIN",
                        "id": "existing-automation-rule-chain-id"
                      },
                      "name": "TestProzorChain"
                    }
                  ],
                  "hasNext": false
                }
                """);
        expectPost(context.server, HOST + "/api/ruleChain/metadata", "Bearer jwt-provisioning-token", "");
        expectGet(context.server, HOST + "/api/deviceProfiles?pageSize=100&page=0", "Bearer jwt-provisioning-token", """
                {
                  "data": [
                    {
                      "id": {
                        "entityType": "DEVICE_PROFILE",
                        "id": "existing-window-profile-id"
                      },
                      "name": "WindowSense Window Profile"
                    }
                  ],
                  "hasNext": false
                }
                """);
        expectGet(context.server, HOST + "/api/deviceProfile/existing-window-profile-id", "Bearer jwt-provisioning-token", """
                {
                  "id": {
                    "entityType": "DEVICE_PROFILE",
                    "id": "existing-window-profile-id"
                  },
                  "name": "WindowSense Window Profile"
                }
                """);
        expectPost(context.server, HOST + "/api/deviceProfile", "Bearer jwt-provisioning-token", "");
        expectPost(context.server, HOST + "/api/device", "Bearer jwt-provisioning-token", """
                {
                  "id": {
                    "entityType": "DEVICE",
                    "id": "device-id"
                  }
                }
                """);
        expectGet(context.server, HOST + "/api/device/device-id/credentials", "Bearer jwt-provisioning-token", """
                {
                  "credentialsType": "ACCESS_TOKEN",
                  "credentialsId": "device-access-token"
                }
                """);
        expectPost(context.server, HOST + "/api/relation", "Bearer jwt-provisioning-token", "");
        expectPost(context.server, HOST + "/api/plugins/telemetry/ASSET/asset-id/attributes/SERVER_SCOPE", "Bearer jwt-provisioning-token", "");
        expectVirtualServerAttributesPost(context.server, "Bearer jwt-provisioning-token");
        expectVirtualSharedAttributesPost(context.server, "Bearer jwt-provisioning-token");

        var provisioned = context.service.provisionVirtualRoomDevice(new VirtualRoomProvisioningRequest(
                ROOM_ID,
                "Kuhinja",
                null,
                "WindowSense - Kuhinja",
                List.of("WINDOW_CONTROL"),
                USER_ID,
                "auth0|window-user"
        ));

        assertThat(provisioned.tbDeviceAccessToken()).isEqualTo("device-access-token");
        context.server.verify();
    }

    @Test
    void provisioningUsesProvisioningAuthOnly() {
        TestContext context = context(ProvisioningAuthMode.JWT);
        context.properties.getThingsBoard().setJwtToken("jwt-provisioning-token");

        expectProvisioningCalls(context.server, "Bearer jwt-provisioning-token");

        var provisioned = context.service.provisionVirtualRoomDevice(request());

        assertThat(provisioned.tbDeviceAccessToken()).isEqualTo("device-access-token");
        context.server.verify();
    }

    @Test
    void linkExistingPhysicalDeviceVerifiesDeviceAndCreatesRelationWithoutFetchingCredentials() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");

        expectGet(context.server, HOST + "/api/device/existing-device-id", "ApiKey provisioning-api-key", """
                {
                  "id": {
                    "entityType": "DEVICE",
                    "id": "existing-device-id"
                  }
                }
                """);
        expectPost(context.server, HOST + "/api/relation", "ApiKey provisioning-api-key", "");

        context.service.linkExistingPhysicalDevice(new ExistingPhysicalDeviceLinkRequest(
                ROOM_ID,
                "Kuhinja",
                "asset-id",
                "existing-device-id",
                "ESP32 - Fizicki prototip",
                null,
                USER_ID,
                "auth0|window-user"
        ));

        context.server.verify();
    }

    @Test
    void linkExistingPhysicalDeviceReportsBadRequestFromRelationStep() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");

        expectGet(context.server, HOST + "/api/device/existing-device-id", "ApiKey provisioning-api-key", """
                {
                  "id": {
                    "entityType": "DEVICE",
                    "id": "existing-device-id"
                  }
                }
                """);
        context.server.expect(once(), requestTo(HOST + "/api/relation"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Authorization", "ApiKey provisioning-api-key"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Invalid relation body\"}"));

        assertThatThrownBy(() -> context.service.linkExistingPhysicalDevice(new ExistingPhysicalDeviceLinkRequest(
                ROOM_ID,
                "Kuhinja",
                "asset-id",
                "existing-device-id",
                "ESP32 - Fizicki prototip",
                null,
                USER_ID,
                "auth0|window-user"
        )))
                .isInstanceOf(ThingsBoardProvisioningException.class)
                .hasMessageContaining("ThingsBoard nije prihvatio provisioning zahtjev kod koraka: create physical device relation.")
                .hasMessageContaining("Invalid relation body");

        context.server.verify();
    }

    @Test
    void provisionsPhysicalEspDeviceWithGeneratedAccessToken() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");

        expectPost(context.server, HOST + "/api/device", "ApiKey provisioning-api-key", """
                {
                  "id": {
                    "entityType": "DEVICE",
                    "id": "physical-device-id"
                  }
                }
                """);
        expectGet(context.server, HOST + "/api/device/physical-device-id/credentials", "ApiKey provisioning-api-key", """
                {
                  "id": {
                    "id": "physical-credentials-id"
                  },
                  "credentialsType": "ACCESS_TOKEN",
                  "credentialsId": "generated-by-thingsboard"
                }
                """);
        expectPost(context.server, HOST + "/api/device/credentials", "ApiKey provisioning-api-key", "");
        expectPost(context.server, HOST + "/api/relation", "ApiKey provisioning-api-key", "");
        expectPost(context.server, HOST + "/api/plugins/telemetry/DEVICE/physical-device-id/attributes/SERVER_SCOPE", "ApiKey provisioning-api-key", "");
        expectPost(context.server, HOST + "/api/plugins/telemetry/DEVICE/physical-device-id/attributes/SHARED_SCOPE", "ApiKey provisioning-api-key", "");

        var provisioned = context.service.provisionPhysicalEspDevice(new PhysicalEspProvisioningRequest(
                ROOM_ID,
                "Kuhinja",
                "asset-id",
                "ESP32 - Kuhinja",
                "WS-ESP32-0001",
                "esp32-chip-id",
                "1.0.0",
                List.of("window", "blinds", "rain"),
                USER_ID,
                "auth0|window-user"
        ));

        assertThat(provisioned.tbDeviceId()).isEqualTo("physical-device-id");
        assertThat(provisioned.tbDeviceAccessToken()).startsWith("ws_");
        context.server.verify();
    }

    @Test
    void registersPhysicalEspDeviceWithProvidedAccessToken() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");

        expectPost(context.server, HOST + "/api/device", "ApiKey provisioning-api-key", """
                {
                  "id": {
                    "entityType": "DEVICE",
                    "id": "registered-device-id"
                  }
                }
                """);
        expectGet(context.server, HOST + "/api/device/registered-device-id/credentials", "ApiKey provisioning-api-key", """
                {
                  "id": {
                    "id": "registered-credentials-id"
                  },
                  "credentialsType": "ACCESS_TOKEN",
                  "credentialsId": "generated-by-thingsboard"
                }
                """);
        expectPost(context.server, HOST + "/api/device/credentials", "ApiKey provisioning-api-key", "");
        expectPost(context.server, HOST + "/api/plugins/telemetry/DEVICE/registered-device-id/attributes/SERVER_SCOPE", "ApiKey provisioning-api-key", "");
        expectPost(context.server, HOST + "/api/plugins/telemetry/DEVICE/registered-device-id/attributes/SHARED_SCOPE", "ApiKey provisioning-api-key", "");

        var registered = context.service.registerPhysicalEspDeviceWithToken(new PhysicalEspTokenRegistrationRequest(
                "ESP32 - Kuhinja",
                "WS-ESP32-0001",
                "esp32-chip-id",
                "1.0.0",
                List.of("window", "blinds"),
                "hardcoded-token-on-esp"
        ));

        assertThat(registered.tbDeviceId()).isEqualTo("registered-device-id");
        context.server.verify();
    }

    @Test
    void jwtAuthModeReportsExpiredTokenOnUnauthorizedResponse() {
        TestContext context = context(ProvisioningAuthMode.JWT);
        context.properties.getThingsBoard().setJwtToken("expired-jwt-token");

        context.server.expect(once(), requestTo(HOST + "/api/asset"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Authorization", "Bearer expired-jwt-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> context.service.provisionVirtualRoomDevice(request()))
                .isInstanceOf(ThingsBoardProvisioningException.class)
                .hasMessage("ThingsBoard JWT token je istekao ili nije valjan. Obnovite THINGSBOARD_JWT_TOKEN.");

        context.server.verify();
    }

    @Test
    void deprovisionVirtualRoomSetsSoftDeleteAttributes() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");

        expectPost(context.server, HOST + "/api/plugins/telemetry/ASSET/asset-id/attributes/SERVER_SCOPE", "ApiKey provisioning-api-key", "");
        expectPost(context.server, HOST + "/api/plugins/telemetry/DEVICE/device-id/attributes/SERVER_SCOPE", "ApiKey provisioning-api-key", "");
        expectPost(context.server, HOST + "/api/plugins/telemetry/DEVICE/device-id/attributes/SHARED_SCOPE", "ApiKey provisioning-api-key", "");

        context.service.deprovisionVirtualRoom(new VirtualRoomDeprovisioningRequest(
                ROOM_ID,
                "Kuhinja",
                "asset-id",
                "device-id"
        ));

        context.server.verify();
    }

    @Test
    void deprovisionVirtualRoomReportsFailure() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");

        context.server.expect(once(), requestTo(HOST + "/api/plugins/telemetry/ASSET/asset-id/attributes/SERVER_SCOPE"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Authorization", "ApiKey provisioning-api-key"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> context.service.deprovisionVirtualRoom(new VirtualRoomDeprovisioningRequest(
                ROOM_ID,
                "Kuhinja",
                "asset-id",
                "device-id"
        )))
                .isInstanceOf(ThingsBoardProvisioningException.class)
                .hasMessage("ThingsBoard deprovisioning nije uspio kod koraka: mark asset deleted.");

        context.server.verify();
    }

    @Test
    void deprovisionRoomDeviceSetsSoftDeleteAttributesWithoutDeletingAsset() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");

        expectPost(context.server, HOST + "/api/plugins/telemetry/DEVICE/device-id/attributes/SERVER_SCOPE", "ApiKey provisioning-api-key", "");
        expectPost(context.server, HOST + "/api/plugins/telemetry/DEVICE/device-id/attributes/SHARED_SCOPE", "ApiKey provisioning-api-key", "");

        context.service.deprovisionRoomDevice(new RoomDeviceDeprovisioningRequest(
                ROOM_ID,
                "Kuhinja",
                "asset-id",
                "device-id"
        ));

        context.server.verify();
    }

    @Test
    void hardDeleteRoomDeviceRemovesRelationAndDeviceWithoutDeletingAsset() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");
        context.properties.getThingsBoard().setDeleteMode(ThingsBoardDeleteMode.HARD);

        expectDelete(context.server, relationDeleteUri(), "ApiKey provisioning-api-key", HttpStatus.OK);
        expectDelete(context.server, HOST + "/api/device/device-id", "ApiKey provisioning-api-key", HttpStatus.OK);

        context.service.deprovisionRoomDevice(new RoomDeviceDeprovisioningRequest(
                ROOM_ID,
                "Kuhinja",
                "asset-id",
                "device-id"
        ));

        context.server.verify();
    }

    @Test
    void hardDeleteRemovesRelationDeviceAndAsset() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");
        context.properties.getThingsBoard().setDeleteMode(ThingsBoardDeleteMode.HARD);

        expectDelete(context.server, relationDeleteUri(), "ApiKey provisioning-api-key", HttpStatus.OK);
        expectDelete(context.server, HOST + "/api/device/device-id", "ApiKey provisioning-api-key", HttpStatus.OK);
        expectDelete(context.server, HOST + "/api/asset/asset-id", "ApiKey provisioning-api-key", HttpStatus.OK);

        context.service.deprovisionVirtualRoom(new VirtualRoomDeprovisioningRequest(
                ROOM_ID,
                "Kuhinja",
                "asset-id",
                "device-id"
        ));

        context.server.verify();
    }

    @Test
    void hardDeleteTreatsNotFoundAsAlreadyDeleted() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");
        context.properties.getThingsBoard().setDeleteMode(ThingsBoardDeleteMode.HARD);

        expectDelete(context.server, relationDeleteUri(), "ApiKey provisioning-api-key", HttpStatus.NOT_FOUND);
        expectDelete(context.server, HOST + "/api/device/device-id", "ApiKey provisioning-api-key", HttpStatus.NOT_FOUND);
        expectDelete(context.server, HOST + "/api/asset/asset-id", "ApiKey provisioning-api-key", HttpStatus.NOT_FOUND);

        context.service.deprovisionVirtualRoom(new VirtualRoomDeprovisioningRequest(
                ROOM_ID,
                "Kuhinja",
                "asset-id",
                "device-id"
        ));

        context.server.verify();
    }

    @Test
    void hardDeleteContinuesWhenRelationDeleteReturnsInternalServerError() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");
        context.properties.getThingsBoard().setDeleteMode(ThingsBoardDeleteMode.HARD);

        expectDelete(context.server, relationDeleteUri(), "ApiKey provisioning-api-key", HttpStatus.INTERNAL_SERVER_ERROR);
        expectDelete(context.server, HOST + "/api/device/device-id", "ApiKey provisioning-api-key", HttpStatus.OK);
        expectDelete(context.server, HOST + "/api/asset/asset-id", "ApiKey provisioning-api-key", HttpStatus.OK);

        context.service.deprovisionVirtualRoom(new VirtualRoomDeprovisioningRequest(
                ROOM_ID,
                "Kuhinja",
                "asset-id",
                "device-id"
        ));

        context.server.verify();
    }

    private static TestContext context(ProvisioningAuthMode authMode) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WindowSenseProperties properties = new WindowSenseProperties();
        properties.getThingsBoard().setHost(HOST);
        properties.getThingsBoard().setProvisioningEnabled(true);
        properties.getThingsBoard().setProvisioningAuthMode(authMode);
        return new TestContext(
                properties,
                server,
                new ThingsBoardRestProvisioningService(properties, builder)
        );
    }

    private static void expectLogin(MockRestServiceServer server) {
        server.expect(once(), requestTo(HOST + "/api/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "token": "jwt-from-login"
                        }
                        """, MediaType.APPLICATION_JSON));
    }

    private static void expectProvisioningCalls(MockRestServiceServer server, String authorization) {
        expectPost(server, HOST + "/api/asset", authorization, """
                {
                  "id": {
                    "entityType": "ASSET",
                    "id": "asset-id"
                  }
                }
                """);
        expectPost(server, HOST + "/api/device", authorization, """
                {
                  "id": {
                    "entityType": "DEVICE",
                    "id": "device-id"
                  }
                }
                """);
        expectGet(server, HOST + "/api/device/device-id/credentials", authorization, """
                {
                  "credentialsType": "ACCESS_TOKEN",
                  "credentialsId": "device-access-token"
                }
                """);
        expectPost(server, HOST + "/api/relation", authorization, "");
        expectPost(server, HOST + "/api/plugins/telemetry/ASSET/asset-id/attributes/SERVER_SCOPE", authorization, "");
        expectVirtualServerAttributesPost(server, authorization);
        expectVirtualSharedAttributesPost(server, authorization);
    }

    private static void expectVirtualServerAttributesPost(MockRestServiceServer server, String authorization) {
        server.expect(once(), requestTo(HOST + "/api/plugins/telemetry/DEVICE/device-id/attributes/SERVER_SCOPE"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Authorization", authorization))
                .andExpect(content().json("""
                        {
                          "isVirtual": true,
                          "roomId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                          "roomName": "Kuhinja",
                          "appUserId": "11111111-2222-3333-4444-555555555555",
                          "auth0Sub": "auth0|window-user",
                          "deviceType": "VIRTUAL",
                          "active": true,
                          "deletedFromApp": false
                        }
                        """, true))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
    }

    private static void expectVirtualSharedAttributesPost(MockRestServiceServer server, String authorization) {
        server.expect(once(), requestTo(HOST + "/api/plugins/telemetry/DEVICE/device-id/attributes/SHARED_SCOPE"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Authorization", authorization))
                .andExpect(content().json("""
                        {
                          "isVirtual": true,
                          "roomId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                          "roomName": "Kuhinja",
                          "deviceType": "VIRTUAL",
                          "deletedFromApp": false,
                          "day": 1,
                          "desiredAngle": 70,
                          "desiredAngleDay": 90,
                          "desiredAngleNight": 0,
                          "desiredAngleRain": 15,
                          "desiredRainProbability": 70,
                          "manualMode": false,
                          "rainProbability": 77
                        }
                        """, true))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
    }

    private static void expectPost(MockRestServiceServer server, String uri, String authorization, String responseBody) {
        server.expect(once(), requestTo(uri))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Authorization", authorization))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private static void expectGet(MockRestServiceServer server, String uri, String authorization, String responseBody) {
        server.expect(once(), requestTo(uri))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Authorization", authorization))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private static void expectDelete(MockRestServiceServer server, String uri, String authorization, HttpStatus status) {
        server.expect(once(), requestTo(uri))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Authorization", authorization))
                .andRespond(withStatus(status));
    }

    private static String relationDeleteUri() {
        return HOST + "/api/relation?fromId=asset-id&fromType=ASSET&relationType=Contains&relationTypeGroup=COMMON&toId=device-id&toType=DEVICE";
    }

    private static VirtualRoomProvisioningRequest request() {
        return new VirtualRoomProvisioningRequest(
                ROOM_ID,
                "Kuhinja",
                "WindowSense - Kuhinja",
                USER_ID,
                "auth0|window-user"
        );
    }

    private record TestContext(
            WindowSenseProperties properties,
            MockRestServiceServer server,
            ThingsBoardRestProvisioningService service
    ) {
    }
}
