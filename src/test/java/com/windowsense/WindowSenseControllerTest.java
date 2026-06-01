package com.windowsense;

import com.windowsense.api.ApiExceptionHandler;
import com.windowsense.api.WindowSenseController;
import com.windowsense.automation.AutomationService;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.repository.WindowSenseStateRepository;
import com.windowsense.service.CommandService;
import com.windowsense.service.EventLogService;
import com.windowsense.service.StatePublisher;
import com.windowsense.service.TelemetryService;
import com.windowsense.service.ThresholdService;
import com.windowsense.service.WeatherService;
import com.windowsense.stream.StateStreamService;
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
        WindowSenseStateRepository repository = new WindowSenseStateRepository(properties);
        AutomationService automationService = new AutomationService();
        EventLogService eventLogService = new EventLogService();
        StatePublisher statePublisher = new StatePublisher(event -> {
        });
        CommandService commandService = new CommandService(properties, repository, eventLogService, statePublisher);
        TelemetryService telemetryService = new TelemetryService(
                repository,
                automationService,
                commandService,
                eventLogService,
                statePublisher
        );
        WeatherService weatherService = new WeatherService(
                repository,
                automationService,
                commandService,
                eventLogService,
                statePublisher
        );
        ThresholdService thresholdService = new ThresholdService(
                repository,
                automationService,
                commandService,
                eventLogService,
                statePublisher
        );
        WindowSenseController controller = new WindowSenseController(
                repository,
                telemetryService,
                weatherService,
                thresholdService,
                commandService,
                new StateStreamService(),
                properties
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

    @Test
    void telemetryCanLowerBlindsWhenLightExceedsThreshold() throws Exception {
        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lightLux": 80000,
                                  "blindsPositionPercent": 10
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.decisions[0].target").value("blinds"))
                .andExpect(jsonPath("$.decisions[0].action").value("setPosition"))
                .andExpect(jsonPath("$.decisions[0].positionPercent").value(85));

        mockMvc.perform(get("/api/device/commands")
                        .queryParam("deviceId", "test-device"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commands[0].target").value("blinds"))
                .andExpect(jsonPath("$.commands[0].action").value("setPosition"))
                .andExpect(jsonPath("$.commands[0].positionPercent").value(85));
    }

    @Test
    void telemetryCanRaiseBlindsWhenLightFallsBelowThreshold() throws Exception {
        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lightLux": 30000,
                                  "blindsPositionPercent": 85
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.decisions[0].target").value("blinds"))
                .andExpect(jsonPath("$.decisions[0].action").value("setPosition"))
                .andExpect(jsonPath("$.decisions[0].positionPercent").value(20));

        mockMvc.perform(get("/api/device/commands")
                        .queryParam("deviceId", "test-device"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commands[0].target").value("blinds"))
                .andExpect(jsonPath("$.commands[0].action").value("setPosition"))
                .andExpect(jsonPath("$.commands[0].positionPercent").value(20));
    }

    @Test
    void thresholdsEndpointReturnsCompactResponse() throws Exception {
        mockMvc.perform(post("/api/automation/thresholds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rainProbabilityClose": 45,
                                  "lightLuxShade": 60000
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.thresholds.rainProbabilityClose").value(45))
                .andExpect(jsonPath("$.thresholds.lightLuxShade").value(60000))
                .andExpect(jsonPath("$.decisions").isArray())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.state").doesNotExist())
                .andExpect(jsonPath("$.commandQueue").doesNotExist())
                .andExpect(jsonPath("$.events").doesNotExist());
    }
}
