package com.windowsense.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "windowsense.security.oidc.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesOpenApiSpecification() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("WindowSense API"))
                .andExpect(jsonPath("$.paths['/api/rooms'].get").exists())
                .andExpect(jsonPath("$.paths['/api/esp/{serialNumber}/commands'].get").exists())
                .andExpect(jsonPath("$.paths['/api/esp/{serialNumber}/ack'].post").exists())
                .andExpect(jsonPath("$.paths['/api/device/commands']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/device/ack']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/state']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/telemetry']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/weather']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/automation/thresholds']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/commands']").doesNotExist());
    }

    @Test
    void exposesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
