package com.windowsense;

import com.windowsense.api.ApiExceptionHandler;
import com.windowsense.api.WindowSenseController;
import com.windowsense.automation.AutomationService;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.store.WindowSenseStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WindowSenseControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WindowSenseProperties properties = new WindowSenseProperties();
        properties.setDeviceId("test-device");
        WindowSenseStore store = new WindowSenseStore(properties, new AutomationService());
        WindowSenseController controller = new WindowSenseController(store, properties);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void healthEndpointReturnsServiceMetadata() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.deviceId").value("test-device"));
    }

    @Test
    void telemetryCanTriggerAutomationAndQueueDeviceCommand() throws Exception {
        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rainDetected": true,
                                  "rainIntensity": 80,
                                  "windowOpenPercent": 60
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.decisions[0].target").value("window"))
                .andExpect(jsonPath("$.decisions[0].action").value("close"));

        mockMvc.perform(get("/api/device/commands")
                        .queryParam("deviceId", "test-device"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commands[0].target").value("window"))
                .andExpect(jsonPath("$.commands[0].action").value("close"));
    }
}
