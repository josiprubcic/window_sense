package com.windowsense.integration.thingsboard;

import com.windowsense.config.WindowSenseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ThingsBoardRestRpcServiceTest {

    private static final String HOST = "https://thingsboard.example";

    @Test
    void sendsTwoWayRpcUsingRpcV2Endpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WindowSenseProperties properties = new WindowSenseProperties();
        properties.getThingsBoard().setHost(HOST);
        properties.getThingsBoard().setProvisioningAuthMode(WindowSenseProperties.ProvisioningAuthMode.JWT);
        properties.getThingsBoard().setJwtToken("jwt-token");

        server.expect(once(), requestTo(HOST + "/api/rpc/twoway/device-id"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Authorization", "Bearer jwt-token"))
                .andRespond(withSuccess("""
                        {
                          "status": "EXECUTED",
                          "target": "window"
                        }
                        """, MediaType.APPLICATION_JSON));

        ThingsBoardRestRpcService service = new ThingsBoardRestRpcService(
                properties,
                new ThingsBoardAuthClient(properties, builder),
                builder
        );

        ThingsBoardRpcResult result = service.sendTwoWayRpc(
                "device-id",
                new ThingsBoardRpcRequest("openWindow", Map.of(), 15000, false)
        );

        assertThat(result.status()).isEqualTo("EXECUTED");
        assertThat(result.deviceResponse()).containsEntry("target", "window");
        server.verify();
    }
}
