package com.windowsense.controller;

import com.windowsense.repository.RuntimeStateRepository;
import com.windowsense.integration.thingsboard.ThingsBoardRpcRequest;
import com.windowsense.integration.thingsboard.ThingsBoardRpcResult;
import com.windowsense.integration.thingsboard.ThingsBoardRpcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "windowsense.security.oidc.enabled=false",
        "windowsense.things-board.provisioning-enabled=false",
        "windowsense.things-board.host=http://thingsboard.local",
        "windowsense.things-board.provisioning-auth-mode=jwt",
        "windowsense.things-board.jwt-token=test-jwt",
        "windowsense.commands.physical-delivery=thingsboard-rpc",
        "windowsense.commands.rpc.enabled=true",
        "windowsense.commands.rpc.timeout-ms=15000",
        "windowsense.commands.rpc.persistent=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomControllerRpcModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RuntimeStateRepository stateRepository;

    @MockBean
    private ThingsBoardRpcService rpcService;

    @BeforeEach
    void setUp() {
        stateRepository.withState(state -> {
            state.commandQueue.clear();
            return null;
        });
        jdbcTemplate.update("delete from window_device");
        jdbcTemplate.update("delete from physical_device_registry");
        jdbcTemplate.update("delete from room");
        jdbcTemplate.update("delete from home");
        jdbcTemplate.update("delete from app_user");
    }

    @Test
    @WithMockUser(username = "auth0|window-user")
    void physicalRoomCommandUsesThingsBoardRpcWithoutQueueOrTokenLeak() throws Exception {
        when(rpcService.sendTwoWayRpc(eq("physical-device-kuhinja"), any(ThingsBoardRpcRequest.class)))
                .thenReturn(ThingsBoardRpcResult.executed(Map.of(
                        "status", "EXECUTED",
                        "commandId", "device-confirmed",
                        "windowPosition", 0
                )));
        String roomId = createRoom("Kuhinja");
        connectPhysicalDevice(roomId, "ESP32 - Kuhinja", "physical-device-kuhinja");

        MvcResult result = mockMvc.perform(post("/api/rooms/{roomId}/commands", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target": "window",
                                  "action": "close"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.delivery").value("THINGSBOARD_RPC"))
                .andExpect(jsonPath("$.tbDeviceId").value("physical-device-kuhinja"))
                .andExpect(jsonPath("$.deviceResponse.status").value("EXECUTED"))
                .andExpect(jsonPath("$.deviceResponse.windowPosition").value(0))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("test-jwt");
        assertThat(stateRepository.getState().commandQueue).isEmpty();

        ArgumentCaptor<ThingsBoardRpcRequest> requestCaptor = ArgumentCaptor.forClass(ThingsBoardRpcRequest.class);
        verify(rpcService).sendTwoWayRpc(eq("physical-device-kuhinja"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().method()).isEqualTo("closeWindow");
        assertThat(requestCaptor.getValue().params()).containsEntry("position", 0.0);
        assertThat(requestCaptor.getValue().params()).containsKey("commandId");
    }

    private String createRoom(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.devices", hasSize(0)))
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void connectPhysicalDevice(String roomId, String name, String tbDeviceId) throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/devices/physical", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "tbDeviceId": "%s",
                                  "capabilities": ["window"]
                                }
                                """.formatted(name, tbDeviceId)))
                .andExpect(status().isCreated());
    }
}
