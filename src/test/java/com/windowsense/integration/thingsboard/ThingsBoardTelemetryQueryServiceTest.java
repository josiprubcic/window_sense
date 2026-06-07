package com.windowsense.integration.thingsboard;

import com.windowsense.exception.ThingsBoardProvisioningException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.config.WindowSenseProperties.ProvisioningAuthMode;
import com.windowsense.integration.thingsboard.ThingsBoardTelemetryQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ThingsBoardTelemetryQueryServiceTest {

    private static final String HOST = "https://thingsboard.example";
    private static final String TELEMETRY_URI = HOST + "/api/plugins/telemetry/DEVICE/device-id/values/timeseries?keys="
            + "rainDetected,rainIntensity,rainRiskPercent,rainProbability,lux,lightLux,indoorTempC,windKmh,windKph,windowOpenPercent,blindClosedPercent,blindsPositionPercent,roomId,roomName,isVirtual";

    @Test
    void parsesLatestTelemetryResponse() {
        TestContext context = context();
        context.server.expect(once(), requestTo(TELEMETRY_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Authorization", "ApiKey provisioning-api-key"))
                .andRespond(withSuccess("""
                        {
                          "rainDetected": [{"ts": 1780480000000, "value": "true"}],
                          "lux": [{"ts": 1780480001000, "value": "42000"}],
                          "indoorTempC": [{"ts": 1780480002000, "value": "24.5"}],
                          "roomName": [{"ts": 1780480003000, "value": "Kuhinja"}]
                        }
                        """, MediaType.APPLICATION_JSON));

        ThingsBoardTelemetryQueryService.LatestTelemetry latest = context.service.latestDeviceTelemetry("device-id");

        assertThat(latest.telemetry())
                .containsEntry("rainDetected", true)
                .containsEntry("lux", 42000L)
                .containsEntry("indoorTempC", 24.5)
                .containsEntry("roomName", "Kuhinja");
        assertThat(latest.updatedAt()).isEqualTo(Instant.ofEpochMilli(1780480003000L));
        context.server.verify();
    }

    @Test
    void mapsThingsBoardErrorToControlledException() {
        TestContext context = context();
        context.server.expect(once(), requestTo(TELEMETRY_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> context.service.latestDeviceTelemetry("device-id"))
                .isInstanceOf(ThingsBoardProvisioningException.class)
                .hasMessage("ThingsBoard latest telemetry nije dostupna.");

        context.server.verify();
    }

    private static TestContext context() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WindowSenseProperties properties = new WindowSenseProperties();
        properties.getThingsBoard().setHost(HOST);
        properties.getThingsBoard().setProvisioningEnabled(true);
        properties.getThingsBoard().setProvisioningAuthMode(ProvisioningAuthMode.API_KEY);
        properties.getThingsBoard().setApiKey("provisioning-api-key");
        return new TestContext(
                server,
                new ThingsBoardTelemetryQueryService(properties, builder)
        );
    }

    private record TestContext(
            MockRestServiceServer server,
            ThingsBoardTelemetryQueryService service
    ) {
    }
}
