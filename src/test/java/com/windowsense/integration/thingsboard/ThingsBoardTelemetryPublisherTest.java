package com.windowsense.integration.thingsboard;

import com.windowsense.config.WindowSenseProperties;
import com.windowsense.entity.WindowDevice;
import com.windowsense.security.EncryptionService;
import com.windowsense.service.ThingsBoardTelemetryPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class ThingsBoardTelemetryPublisherTest {

    private static final String HOST = "https://thingsboard.example";
    private static final String KEY = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());

    @Test
    void decryptsTokenAndPostsTelemetryToThingsBoardEndpoint() {
        TestContext context = context();
        String encryptedToken = context.encryptionService.encrypt("device-access-token");
        WindowDevice device = WindowDevice.virtualDevice("WindowSense - Kuhinja", "device-id");
        device.storeEncryptedThingsBoardDeviceToken(encryptedToken);

        context.server.expect(once(), requestTo(HOST + "/api/v1/device-access-token/telemetry"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess());

        context.publisher.publishTelemetry(device, Map.of("rainDetected", true));

        context.server.verify();
    }

    @Test
    void skipsDeviceWhenTokenIsMissing() {
        TestContext context = context();
        WindowDevice device = WindowDevice.virtualDevice("WindowSense - Kuhinja", "device-id");

        context.publisher.publishTelemetry(device, Map.of("rainDetected", true));

        context.server.verify();
    }

    @Test
    void doesNotLogPlaintextOrEncryptedTokenOnThingsBoardError(CapturedOutput output) {
        TestContext context = context();
        String encryptedToken = context.encryptionService.encrypt("device-access-token");
        WindowDevice device = WindowDevice.virtualDevice("WindowSense - Kuhinja", "device-id");
        device.storeEncryptedThingsBoardDeviceToken(encryptedToken);

        context.server.expect(once(), requestTo(HOST + "/api/v1/device-access-token/telemetry"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        context.publisher.publishTelemetry(device, Map.of("rainDetected", true));

        context.server.verify();
        assertThat(output.getAll()).doesNotContain("device-access-token");
        assertThat(output.getAll()).doesNotContain(encryptedToken);
    }

    private static TestContext context() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WindowSenseProperties properties = new WindowSenseProperties();
        properties.getThingsBoard().setHost(HOST);
        properties.getEncryption().setKey(KEY);
        EncryptionService encryptionService = new EncryptionService(properties);
        return new TestContext(
                server,
                encryptionService,
                new ThingsBoardTelemetryPublisher(properties, encryptionService, builder)
        );
    }

    private record TestContext(
            MockRestServiceServer server,
            EncryptionService encryptionService,
            ThingsBoardTelemetryPublisher publisher
    ) {
    }
}
