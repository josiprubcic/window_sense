package com.windowsense.controller;

import com.windowsense.controller.ApiExceptionHandler;
import com.windowsense.controller.WindowSenseController;
import com.windowsense.repository.WindowDeviceRepository;
import com.windowsense.repository.RuntimeStateRepository;
import com.windowsense.service.CommandService;
import com.windowsense.service.DeviceBootstrapService;
import com.windowsense.service.EventLogService;
import com.windowsense.service.RuntimeStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

class WindowSenseControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RuntimeStateRepository repository = new RuntimeStateRepository();
        EventLogService eventLogService = new EventLogService();
        CommandService commandService = new CommandService(repository, eventLogService, mock(WindowDeviceRepository.class));
        WindowSenseController controller = new WindowSenseController(
                new RuntimeStateService(repository),
                commandService,
                mock(DeviceBootstrapService.class)
        );

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
                .andExpect(jsonPath("$.deviceId").doesNotExist());
    }

    @Test
    void legacyStateEndpointReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/state"))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyTelemetryEndpointReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "indoorTempC": 28.5
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyWeatherEndpointReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/weather")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rainProbability": 80
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyThresholdsEndpointReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/automation/thresholds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rainProbabilityClose": 45
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyGlobalCommandEndpointReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target": "window",
                                  "action": "close"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyDevicePollingEndpointReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/device/commands"))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyDeviceAckEndpointReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/device/ack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commandId": "cmd-test",
                                  "status": "executed"
                                }
                                """))
                .andExpect(status().isNotFound());
    }
}
