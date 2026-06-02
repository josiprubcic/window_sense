package com.windowsense;

import com.windowsense.common.ThingsBoardProvisioningException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.config.WindowSenseProperties.ProvisioningAuthMode;
import com.windowsense.thingsboard.ThingsBoardRestProvisioningService;
import com.windowsense.thingsboard.VirtualRoomProvisioningRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
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
    void passwordAuthModeUsesLoginToken() {
        TestContext context = context(ProvisioningAuthMode.PASSWORD);
        context.properties.getThingsBoard().setUsername("tenant@example.com");
        context.properties.getThingsBoard().setPassword("secret-password");

        expectLogin(context.server);
        expectProvisioningCalls(context.server, "Bearer jwt-from-login");

        context.service.provisionVirtualRoomDevice(request());

        context.server.verify();
    }

    @Test
    void jwtAuthModeUsesBearerHeaderWithoutLogin() {
        TestContext context = context(ProvisioningAuthMode.JWT);
        context.properties.getThingsBoard().setJwtToken("jwt-provisioning-token");
        context.properties.getThingsBoard().setAccessToken("legacy-device-token");

        expectProvisioningCalls(context.server, "Bearer jwt-provisioning-token");

        context.service.provisionVirtualRoomDevice(request());

        context.server.verify();
    }

    @Test
    void apiKeyAuthModeUsesApiKeyHeaderWithoutLogin() {
        TestContext context = context(ProvisioningAuthMode.API_KEY);
        context.properties.getThingsBoard().setApiKey("provisioning-api-key");

        expectProvisioningCalls(context.server, "ApiKey provisioning-api-key");

        context.service.provisionVirtualRoomDevice(request());

        context.server.verify();
    }

    @Test
    void provisioningDoesNotUseLegacyDeviceAccessToken() {
        TestContext context = context(ProvisioningAuthMode.JWT);
        context.properties.getThingsBoard().setJwtToken("jwt-provisioning-token");
        context.properties.getThingsBoard().setAccessToken("legacy-device-token");

        expectProvisioningCalls(context.server, "Bearer jwt-provisioning-token");

        context.service.provisionVirtualRoomDevice(request());

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
        expectPost(server, HOST + "/api/relation", authorization, "");
        expectPost(server, HOST + "/api/plugins/telemetry/DEVICE/device-id/attributes/SERVER_SCOPE", authorization, "");
        expectPost(server, HOST + "/api/plugins/telemetry/DEVICE/device-id/attributes/SHARED_SCOPE", authorization, "");
    }

    private static void expectPost(MockRestServiceServer server, String uri, String authorization, String responseBody) {
        server.expect(once(), requestTo(uri))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Authorization", authorization))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
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
