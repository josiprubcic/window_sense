package com.windowsense.controller;

import com.jayway.jsonpath.JsonPath;
import com.windowsense.service.PhysicalDevicePairingCodeHasher;
import com.windowsense.service.PhysicalDeviceSecretHasher;
import com.windowsense.repository.RuntimeStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "windowsense.security.oidc.enabled=false",
        "windowsense.things-board.provisioning-enabled=false",
        "windowsense.commands.physical-delivery=polling"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RuntimeStateRepository stateRepository;

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
    void createRoomCreatesRoomWithoutDevice() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Dnevni boravak"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Dnevni boravak"))
                .andExpect(jsonPath("$.tbAssetId").exists())
                .andExpect(jsonPath("$.activeDevice").doesNotExist())
                .andExpect(jsonPath("$.devices", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "auth0|window-user")
    void createRoomRejectsDuplicateRoomInSameHome() throws Exception {
        createRoom("Spavaca soba");

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Spavaca soba"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Soba s tim nazivom vec postoji u objektu."));
    }

    @Test
    void connectPhysicalDeviceCreatesPhysicalDeviceForOwnedRoom() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Kuhinja");

        MvcResult result = mockMvc.perform(post("/api/rooms/{roomId}/devices/physical", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Fizicki prototip",
                                  "tbDeviceId": "existing-thingsboard-device-uuid"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(roomId))
                .andExpect(jsonPath("$.activeDevice.deviceType").value("PHYSICAL"))
                .andExpect(jsonPath("$.devices", hasSize(1)))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> deviceTypes = JsonPath.read(body, "$.devices[*].deviceType");
        List<Boolean> virtualFlags = JsonPath.read(body, "$.devices[*].isVirtual");
        List<String> statuses = JsonPath.read(body, "$.devices[*].status");
        List<String> tbDeviceIds = JsonPath.read(body, "$.devices[*].tbDeviceId");
        assertThat(deviceTypes).contains("PHYSICAL");
        assertThat(virtualFlags).contains(false);
        assertThat(statuses).contains("ACTIVE");
        assertThat(tbDeviceIds).contains("existing-thingsboard-device-uuid");

        mockMvc.perform(get("/api/rooms")
                        .with(user("auth0|window-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].devices", hasSize(1)));
    }

    @Test
    void connectPhysicalDeviceReturnsNotFoundForForeignRoom() throws Exception {
        String roomId = createRoomAs("auth0|first-user", "Kuhinja");

        mockMvc.perform(post("/api/rooms/{roomId}/devices/physical", roomId)
                        .with(user("auth0|second-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Fizicki prototip",
                                  "tbDeviceId": "existing-thingsboard-device-uuid"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Soba nije pronadjena."));
    }

    @Test
    void connectPhysicalDeviceAllowsMultipleActivePhysicalDevicesInRoom() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Kuhinja");
        connectPhysicalDevice(roomId, "ESP32 - Fizicki prototip", "existing-thingsboard-device-uuid");

        mockMvc.perform(post("/api/rooms/{roomId}/devices/physical", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Drugi prototip",
                                  "tbDeviceId": "another-thingsboard-device-uuid"
                                }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.devices", hasSize(2)));
    }

    @Test
    void addVirtualDeviceCreatesVirtualDeviceForExistingRoom() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Demo soba");

        mockMvc.perform(post("/api/rooms/{roomId}/devices/virtual", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Virtualni uredjaj"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(roomId))
                .andExpect(jsonPath("$.activeDevice.deviceType").value("VIRTUAL"))
                .andExpect(jsonPath("$.activeDevice.isVirtual").value(true))
                .andExpect(jsonPath("$.devices", hasSize(1)))
                .andExpect(jsonPath("$.devices[0].name").value("Virtualni uredjaj"))
                .andExpect(jsonPath("$.devices[0].deviceType").value("VIRTUAL"))
                .andExpect(jsonPath("$.devices[0].status").value("ACTIVE"));
    }

    @Test
    void pairPhysicalDeviceClaimsClaimableRegistryDeviceForOwnedRoom() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Dnevni boravak");
        insertRegistryDevice("WS-SN-0001", "WS-DEMO-0001", "tb-device-physical-1", "CLAIMABLE");

        MvcResult result = mockMvc.perform(post("/api/rooms/{roomId}/devices/pair", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Dnevni boravak",
                                  "serialNumber": "WS-SN-0001",
                                  "pairingCode": "ws-demo-0001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(roomId))
                .andExpect(jsonPath("$.activeDevice.deviceType").value("PHYSICAL"))
                .andExpect(jsonPath("$.devices", hasSize(1)))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> deviceTypes = JsonPath.read(body, "$.devices[*].deviceType");
        List<Boolean> virtualFlags = JsonPath.read(body, "$.devices[*].isVirtual");
        List<String> tbDeviceIds = JsonPath.read(body, "$.devices[*].tbDeviceId");
        assertThat(deviceTypes).contains("PHYSICAL");
        assertThat(virtualFlags).contains(false);
        assertThat(tbDeviceIds).contains("tb-device-physical-1");
        assertThat(jdbcTemplate.queryForObject(
                "select tb_device_token_encrypted from window_device where room_id = ? and device_type = 'PHYSICAL'",
                String.class,
                UUID.fromString(roomId)
        )).isNull();

        assertThat(jdbcTemplate.queryForObject(
                "select status from physical_device_registry where serial_number = ?",
                String.class,
                "WS-SN-0001"
        )).isEqualTo("CLAIMED");
        assertThat(jdbcTemplate.queryForObject(
                "select claimed_room_id from physical_device_registry where serial_number = ?",
                UUID.class,
                "WS-SN-0001"
        )).isEqualTo(UUID.fromString(roomId));
        assertThat(jdbcTemplate.queryForObject(
                "select claimed_by_user_id from physical_device_registry where serial_number = ?",
                UUID.class,
                "WS-SN-0001"
        )).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select claimed_at from physical_device_registry where serial_number = ?",
                java.time.Instant.class,
                "WS-SN-0001"
        )).isNotNull();
    }

    @Test
    void addPhysicalDeviceByTokenUsesFactoryRegisteredCapabilities() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Dnevni boravak");
        insertRegistryDevice(
                "WS-BLINDS-0001",
                "WS-DEMO-BLINDS",
                "tb-device-blinds-1",
                "CLAIMABLE",
                "roleta-demo-token",
                "BLINDS_CONTROL"
        );

        MvcResult result = mockMvc.perform(post("/api/rooms/{roomId}/devices/token", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Roleta dnevna",
                                  "thingsBoardAccessToken": "roleta-demo-token"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(roomId))
                .andExpect(jsonPath("$.activeDevice.deviceType").value("PHYSICAL"))
                .andExpect(jsonPath("$.devices", hasSize(1)))
                .andExpect(jsonPath("$.devices[0].serialNumber").value("WS-BLINDS-0001"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> capabilities = JsonPath.read(body, "$.devices[0].capabilities[*]");
        assertThat(capabilities).contains("BLINDS_CONTROL");
        assertThat(capabilities).doesNotContain("WINDOW_CONTROL");

        assertThat(jdbcTemplate.queryForObject(
                "select status from physical_device_registry where serial_number = ?",
                String.class,
                "WS-BLINDS-0001"
        )).isEqualTo("CLAIMED");
        assertThat(jdbcTemplate.queryForObject(
                "select physical_hardware_id from window_device where room_id = ? and tb_device_id = ?",
                String.class,
                UUID.fromString(roomId),
                "tb-device-blinds-1"
        )).isEqualTo("WS-BLINDS-0001");
    }

    @Test
    void provisionPhysicalEspCreatesThingsBoardDeviceAndReturnsBootstrapSessionWithoutToken() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Kuhinja");
        String pairingCode = "WS-SETUP-0001";
        String deviceSecret = "esp-secret-random-value";

        MvcResult result = mockMvc.perform(post("/api/rooms/{roomId}/devices/provision-physical", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Kuhinja",
                                  "pairingCode": "%s",
                                  "pairingCodeHash": "%s",
                                  "serialNumber": "WS-ESP32-0001",
                                  "hardwareId": "esp32-chip-id",
                                  "firmwareVersion": "1.0.0",
                                  "capabilities": ["window", "blinds", "rain"],
                                  "deviceSecretHash": "%s"
                                }
                                """.formatted(
                                pairingCode,
                                PhysicalDevicePairingCodeHasher.hash(pairingCode),
                                PhysicalDeviceSecretHasher.hash(deviceSecret)
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.tbDeviceId").exists())
                .andExpect(jsonPath("$.serialNumber").value("WS-ESP32-0001"))
                .andExpect(jsonPath("$.hardwareId").value("esp32-chip-id"))
                .andExpect(jsonPath("$.status").value("AWAITING_DEVICE_BOOTSTRAP"))
                .andExpect(jsonPath("$.provisioningSessionId").exists())
                .andExpect(jsonPath("$.thingsBoardAccessToken").doesNotExist())
                .andExpect(jsonPath("$.tbDeviceAccessToken").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("esp-secret-random-value");
        assertThat(body).doesNotContain(PhysicalDeviceSecretHasher.hash(deviceSecret));

        mockMvc.perform(get("/api/rooms")
                        .with(user("auth0|window-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].devices[?(@.deviceType == 'PHYSICAL')]", hasSize(1)));

        Integer claimedCount = jdbcTemplate.queryForObject("""
                select count(*) from physical_device_registry
                where serial_number = 'WS-ESP32-0001'
                  and status = 'CLAIMED'
                  and provisioning_session_hash is not null
                """, Integer.class);
        assertThat(claimedCount).isEqualTo(1);
    }

    @Test
    void pairPhysicalDeviceReturnsNotFoundForInvalidPairingCode() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Dnevni boravak");
        insertRegistryDevice("WS-SN-UNKNOWN", "WS-CORRECT", "tb-device-physical-x", "CLAIMABLE");

        mockMvc.perform(post("/api/rooms/{roomId}/devices/pair", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Dnevni boravak",
                                  "serialNumber": "WS-SN-UNKNOWN",
                                  "pairingCode": "WS-UNKNOWN"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Kod za povezivanje nije valjan."));
    }

    @Test
    void pairPhysicalDeviceReturnsConflictForAlreadyClaimedCode() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Dnevni boravak");
        insertRegistryDevice("WS-SN-0002", "WS-DEMO-0002", "tb-device-physical-2", "CLAIMED");

        mockMvc.perform(post("/api/rooms/{roomId}/devices/pair", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Dnevni boravak",
                                  "serialNumber": "WS-SN-0002",
                                  "pairingCode": "WS-DEMO-0002"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DEVICE_ALREADY_CLAIMED"));
    }

    @Test
    void pairPhysicalDeviceReturnsConflictForDisabledDevice() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Dnevni boravak");
        insertRegistryDevice("WS-SN-0003", "WS-DEMO-0003", "tb-device-physical-3", "DISABLED");

        mockMvc.perform(post("/api/rooms/{roomId}/devices/pair", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Dnevni boravak",
                                  "serialNumber": "WS-SN-0003",
                                  "pairingCode": "WS-DEMO-0003"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Uredjaj je deaktiviran."));
    }

    @Test
    void pairPhysicalDeviceReturnsNotFoundForForeignRoom() throws Exception {
        String roomId = createRoomAs("auth0|first-user", "Dnevni boravak");
        insertRegistryDevice("WS-SN-0004", "WS-DEMO-0004", "tb-device-physical-4", "CLAIMABLE");

        mockMvc.perform(post("/api/rooms/{roomId}/devices/pair", roomId)
                        .with(user("auth0|second-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Dnevni boravak",
                                  "serialNumber": "WS-SN-0004",
                                  "pairingCode": "WS-DEMO-0004"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Soba nije pronadjena."));

        assertThat(jdbcTemplate.queryForObject(
                "select status from physical_device_registry where serial_number = ?",
                String.class,
                "WS-SN-0004"
        )).isEqualTo("CLAIMABLE");
    }

    @Test
    void pairPhysicalDeviceAllowsMultipleActivePhysicalDevicesInRoom() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Dnevni boravak");
        insertRegistryDevice("WS-SN-0005", "WS-DEMO-0005", "tb-device-physical-5", "CLAIMABLE");
        insertRegistryDevice("WS-SN-0006", "WS-DEMO-0006", "tb-device-physical-6", "CLAIMABLE");
        pairPhysicalDevice(roomId, "ESP32 - Prvi", "WS-DEMO-0005");

        mockMvc.perform(post("/api/rooms/{roomId}/devices/pair", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Drugi",
                                  "serialNumber": "WS-SN-0006",
                                  "pairingCode": "WS-DEMO-0006"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.devices", hasSize(2)));

        assertThat(jdbcTemplate.queryForObject(
                "select status from physical_device_registry where serial_number = ?",
                String.class,
                "WS-SN-0006"
        )).isEqualTo("CLAIMED");
    }

    @Test
    void listRoomsReturnsOnlyCurrentUserRooms() throws Exception {
        createRoomAs("auth0|first-user", "Kuhinja");
        createRoomAs("auth0|second-user", "Ured");

        mockMvc.perform(get("/api/rooms")
                        .with(user("auth0|first-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Kuhinja"));
    }

    @Test
    @WithMockUser(username = "auth0|window-user")
    void createRoomRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Naziv sobe je obavezan."));
    }

    @Test
    void updateRoomRenamesOwnedRoom() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Dnevni boravak");

        mockMvc.perform(put("/api/rooms/{roomId}", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Blagovaonica"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roomId))
                .andExpect(jsonPath("$.name").value("Blagovaonica"))
                .andExpect(jsonPath("$.activeDevice").doesNotExist())
                .andExpect(jsonPath("$.devices", hasSize(0)));
    }

    @Test
    void updateRoomRejectsDuplicateNameInSameHome() throws Exception {
        createRoomAs("auth0|window-user", "Dnevni boravak");
        String roomId = createRoomAs("auth0|window-user", "Spavaca soba");

        mockMvc.perform(put("/api/rooms/{roomId}", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Dnevni boravak"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Soba s tim nazivom vec postoji u objektu."));
    }

    @Test
    void updateRoomReturnsNotFoundForForeignRoom() throws Exception {
        String roomId = createRoomAs("auth0|first-user", "Kuhinja");

        mockMvc.perform(put("/api/rooms/{roomId}", roomId)
                        .with(user("auth0|second-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Ured"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Soba nije pronadjena."));
    }

    @Test
    void updateRoomRejectsBlankName() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Dnevni boravak");

        mockMvc.perform(put("/api/rooms/{roomId}", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Naziv sobe je obavezan."));
    }

    @Test
    void deleteRoomDeletesOwnedRoom() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Dnevni boravak");

        mockMvc.perform(delete("/api/rooms/{roomId}", roomId)
                        .with(user("auth0|window-user")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rooms")
                        .with(user("auth0|window-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteRoomReturnsNotFoundForForeignRoom() throws Exception {
        String roomId = createRoomAs("auth0|first-user", "Kuhinja");

        mockMvc.perform(delete("/api/rooms/{roomId}", roomId)
                        .with(user("auth0|second-user")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Soba nije pronadjena."));
    }

    @Test
    void latestTelemetryForRoomWithoutDeviceReturnsUnavailable() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Kuhinja");

        mockMvc.perform(get("/api/rooms/{roomId}/telemetry/latest", roomId)
                        .with(user("auth0|window-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.roomName").value("Kuhinja"))
                .andExpect(jsonPath("$.deviceType").doesNotExist())
                .andExpect(jsonPath("$.isVirtual").value(false))
                .andExpect(jsonPath("$.telemetry").isEmpty())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_DEVICE"))
                .andExpect(jsonPath("$.message").value("Nema povezanog uredjaja."));
    }

    @Test
    void latestTelemetryReturnsNotFoundForForeignRoom() throws Exception {
        String roomId = createRoomAs("auth0|first-user", "Kuhinja");

        mockMvc.perform(get("/api/rooms/{roomId}/telemetry/latest", roomId)
                        .with(user("auth0|second-user")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Soba nije pronadjena."));
    }

    @Test
    void roomCommandEnqueuesForOwnedRoomPhysicalDevice() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Kuhinja");
        insertRegistryDevice("WS-SN-KUHINJA", "WS-DEMO-KUHINJA", "physical-device-kuhinja", "CLAIMABLE");
        pairPhysicalDevice(roomId, "ESP32 - Kuhinja", "WS-DEMO-KUHINJA", "WS-SN-KUHINJA");

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
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.deviceId").value("physical-device-kuhinja"))
                .andExpect(jsonPath("$.deviceType").value("PHYSICAL"))
                .andExpect(jsonPath("$.target").value("window"))
                .andExpect(jsonPath("$.action").value("close"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.commandId").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("token", "Token", "jwt", "JWT", "encrypted", "secret");

        mockMvc.perform(get("/api/esp/{serialNumber}/commands", "WS-SN-KUHINJA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commands", hasSize(1)))
                .andExpect(jsonPath("$.commands[0].deviceId").value("physical-device-kuhinja"))
                .andExpect(jsonPath("$.commands[0].target").value("window"))
                .andExpect(jsonPath("$.commands[0].action").value("close"));
    }

    @Test
    void roomCommandAppliesToExplicitVirtualDevice() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Demo soba");
        addVirtualDevice(roomId, "Virtualni uredjaj");
        String virtualDeviceId = jdbcTemplate.queryForObject(
                "select tb_device_id from window_device where room_id = ?",
                String.class,
                UUID.fromString(roomId)
        );

        mockMvc.perform(post("/api/rooms/{roomId}/commands", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target": "blinds",
                                  "action": "setPosition",
                                  "positionPercent": 65
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deviceId").value(virtualDeviceId))
                .andExpect(jsonPath("$.deviceType").value("VIRTUAL"))
                .andExpect(jsonPath("$.target").value("blinds"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.positionPercent").value(65));

        mockMvc.perform(get("/api/rooms/{roomId}/telemetry/latest", roomId)
                        .with(user("auth0|window-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telemetry.blindClosedPercent").value(65.0));
    }

    @Test
    void roomCommandReturnsNotFoundForForeignRoom() throws Exception {
        String roomId = createRoomAs("auth0|first-user", "Kuhinja");

        mockMvc.perform(post("/api/rooms/{roomId}/commands", roomId)
                        .with(user("auth0|second-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target": "window",
                                  "action": "close"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Soba nije pronadjena."));
    }

    @Test
    void roomCommandReturnsNotFoundWhenRoomDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/commands", UUID.randomUUID())
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target": "window",
                                  "action": "close"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Soba nije pronadjena."));
    }

    @Test
    void roomCommandFailsWhenRoomHasNoActiveControllableDevice() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Kuhinja");

        mockMvc.perform(post("/api/rooms/{roomId}/commands", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target": "window",
                                  "action": "close"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_DEVICE_FOR_CAPABILITY"));
    }

    @Test
    void roomCommandRequiresExplicitDeviceWhenMultipleDevicesSupportCapability() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Kuhinja");
        insertRegistryDevice("WS-SN-A", "WS-DEMO-A", "physical-device-a", "CLAIMABLE");
        insertRegistryDevice("WS-SN-B", "WS-DEMO-B", "physical-device-b", "CLAIMABLE");
        pairPhysicalDevice(roomId, "ESP32 - Prozor A", "WS-DEMO-A", "WS-SN-A");
        pairPhysicalDevice(roomId, "ESP32 - Prozor B", "WS-DEMO-B", "WS-SN-B");
        String localDeviceId = jdbcTemplate.queryForObject(
                "select id from window_device where tb_device_id = ?",
                String.class,
                "physical-device-b"
        );

        mockMvc.perform(post("/api/rooms/{roomId}/commands", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target": "window",
                                  "action": "close"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("MULTIPLE_DEVICES_FOR_CAPABILITY"));

        mockMvc.perform(post("/api/rooms/{roomId}/commands", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target": "window",
                                  "action": "close",
                                  "localDeviceId": "%s"
                                }
                                """.formatted(localDeviceId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.localDeviceId").value(localDeviceId))
                .andExpect(jsonPath("$.deviceId").value("physical-device-b"))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(get("/api/esp/{serialNumber}/commands", "WS-SN-B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commands", hasSize(1)))
                .andExpect(jsonPath("$.commands[0].deviceId").value("physical-device-b"));
    }

    @Test
    void espSerialPollingAndAckAreScopedToDevice() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Kuhinja");
        insertRegistryDevice("WS-SN-KUHINJA", "WS-DEMO-KUHINJA", "physical-device-kuhinja", "CLAIMABLE");
        pairPhysicalDevice(roomId, "ESP32 - Kuhinja", "WS-DEMO-KUHINJA", "WS-SN-KUHINJA");

        MvcResult result = mockMvc.perform(post("/api/rooms/{roomId}/commands", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target": "window",
                                  "action": "open"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();
        String commandId = JsonPath.read(result.getResponse().getContentAsString(), "$.commandId");

        insertRegistryDevice("WS-SN-OTHER", "WS-DEMO-OTHER", "other-device", "CLAIMABLE");
        pairPhysicalDevice(roomId, "ESP32 - Drugi", "WS-DEMO-OTHER", "WS-SN-OTHER");

        mockMvc.perform(post("/api/esp/{serialNumber}/ack", "WS-SN-OTHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commandId": "%s",
                                  "status": "executed"
                                }
                                """.formatted(commandId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Komanda nije pronadjena."));

        mockMvc.perform(get("/api/esp/{serialNumber}/commands", "WS-SN-KUHINJA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commands", hasSize(1)))
                .andExpect(jsonPath("$.commands[0].status").value("pending"));

        mockMvc.perform(post("/api/esp/{serialNumber}/ack", "WS-SN-KUHINJA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commandId": "%s",
                                  "status": "executed"
                                }
                                """.formatted(commandId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(commandId))
                .andExpect(jsonPath("$.deviceId").value("physical-device-kuhinja"))
                .andExpect(jsonPath("$.status").value("executed"));

        mockMvc.perform(get("/api/esp/{serialNumber}/commands", "WS-SN-KUHINJA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commands", hasSize(0)));
    }

    @Test
    void espSerialPollingAndAckAreScopedToPairedPhysicalDevice() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Kuhinja");
        insertRegistryDevice("WS-SN-KUHINJA", "WS-DEMO-KUHINJA", "physical-device-kuhinja", "CLAIMABLE");
        pairPhysicalDevice(roomId, "ESP32 - Kuhinja", "WS-DEMO-KUHINJA", "WS-SN-KUHINJA");

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
                .andReturn();
        String commandId = JsonPath.read(result.getResponse().getContentAsString(), "$.commandId");

        mockMvc.perform(get("/api/esp/{serialNumber}/commands", "WS-SN-KUHINJA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commands", hasSize(1)))
                .andExpect(jsonPath("$.commands[0].deviceId").value("physical-device-kuhinja"))
                .andExpect(jsonPath("$.commands[0].target").value("window"))
                .andExpect(jsonPath("$.commands[0].action").value("close"));

        mockMvc.perform(post("/api/esp/{serialNumber}/ack", "WS-SN-OTHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commandId": "%s",
                                  "status": "executed"
                                }
                                """.formatted(commandId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Fizicki uredjaj nije povezan sa sobom."));

        mockMvc.perform(post("/api/esp/{serialNumber}/ack", "WS-SN-KUHINJA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commandId": "%s",
                                  "status": "executed"
                                }
                                """.formatted(commandId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(commandId))
                .andExpect(jsonPath("$.deviceId").value("physical-device-kuhinja"))
                .andExpect(jsonPath("$.status").value("executed"));

        mockMvc.perform(get("/api/esp/{serialNumber}/commands", "WS-SN-KUHINJA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commands", hasSize(0)));
    }

    @Test
    void userCanUpdateSimulationStateForOwnVirtualRoom() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Demo soba");
        addVirtualDevice(roomId, "Virtualni uredjaj");

        mockMvc.perform(patch("/api/rooms/{roomId}/simulation/mode", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "MANUAL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MANUAL"));

        mockMvc.perform(patch("/api/rooms/{roomId}/simulation", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rainDetected": true,
                                  "rainIntensity": 50,
                                  "rainRiskPercent": 80,
                                  "lux": 12000,
                                  "indoorTempC": 24.5,
                                  "windKmh": 20,
                                  "windowOpenPercent": 30,
                                  "blindClosedPercent": 90,
                                  "day": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MANUAL"))
                .andExpect(jsonPath("$.telemetry.rainDetected").value(true))
                .andExpect(jsonPath("$.telemetry.rainIntensity").value(50.0))
                .andExpect(jsonPath("$.telemetry.rainRiskPercent").value(80.0))
                .andExpect(jsonPath("$.telemetry.windKmh").value(20.0))
                .andExpect(jsonPath("$.telemetry.windowOpenPercent").value(0.0))
                .andExpect(jsonPath("$.telemetry.blindClosedPercent").value(90.0))
                .andExpect(jsonPath("$.decisions[0].target").value("window"))
                .andExpect(jsonPath("$.decisions[0].action").value("close"));
    }

    @Test
    void userCannotUpdateAnotherUsersRoomSimulation() throws Exception {
        String roomId = createRoomAs("auth0|first-user", "Demo soba");

        mockMvc.perform(patch("/api/rooms/{roomId}/simulation", roomId)
                        .with(user("auth0|second-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lux": 12000
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Soba nije pronadjena."));
    }

    @Test
    void physicalOnlyRoomSimulationUpdateReturnsClearError() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Kuhinja");
        connectPhysicalDevice(roomId, "ESP32 - Kuhinja", "physical-device-kuhinja");

        mockMvc.perform(patch("/api/rooms/{roomId}/simulation", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lux": 12000
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Soba nema aktivni virtualni uredjaj."));
    }

    @Test
    void mixedPhysicalAndVirtualRoomAllowsSimulationOnVirtualDevice() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Kuhinja");
        connectPhysicalDevice(roomId, "ESP32 - Kuhinja", "physical-device-kuhinja");
        addVirtualDevice(roomId, "Virtualni uredjaj - Kuhinja");

        mockMvc.perform(patch("/api/rooms/{roomId}/simulation/mode", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "MANUAL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceType").value("VIRTUAL"))
                .andExpect(jsonPath("$.mode").value("MANUAL"));

        mockMvc.perform(patch("/api/rooms/{roomId}/simulation", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lux": 12000,
                                  "windowOpenPercent": 20
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceType").value("VIRTUAL"))
                .andExpect(jsonPath("$.telemetry.windowOpenPercent").value(20.0));
    }

    @Test
    void roomSpecificThresholdsCanBeReadAndUpdated() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Demo soba");

        mockMvc.perform(get("/api/rooms/{roomId}/automation/thresholds", roomId)
                        .with(user("auth0|window-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thresholds.rainProbabilityClose").value(70.0));

        mockMvc.perform(put("/api/rooms/{roomId}/automation/thresholds", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rainProbabilityClose": 40,
                                  "windKphClose": 35
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thresholds.rainProbabilityClose").value(40.0))
                .andExpect(jsonPath("$.thresholds.lightLuxShade").doesNotExist())
                .andExpect(jsonPath("$.thresholds.indoorTempShadeC").doesNotExist())
                .andExpect(jsonPath("$.thresholds.windKphClose").value(35.0));
    }

    @Test
    void thresholdUpdateTriggersAutomationUsingCurrentVirtualTelemetry() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Demo soba");
        addVirtualDevice(roomId, "Virtualni uredjaj");

        mockMvc.perform(patch("/api/rooms/{roomId}/simulation", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rainDetected": false,
                                  "rainIntensity": 0,
                                  "rainRiskPercent": 10,
                                  "lux": 24000,
                                  "indoorTempC": 22,
                                  "windKmh": 0,
                                  "windowOpenPercent": 80,
                                  "blindClosedPercent": 20
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telemetry.windowOpenPercent").value(80.0))
                .andExpect(jsonPath("$.telemetry.blindClosedPercent").value(85.0))
                .andExpect(jsonPath("$.decisions", hasSize(1)))
                .andExpect(jsonPath("$.decisions[0].target").value("blinds"))
                .andExpect(jsonPath("$.decisions[0].action").value("setPosition"));

        mockMvc.perform(put("/api/rooms/{roomId}/automation/thresholds", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rainProbabilityClose": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thresholds.rainProbabilityClose").value(5.0))
                .andExpect(jsonPath("$.telemetry.windowOpenPercent").value(0.0))
                .andExpect(jsonPath("$.decisions[0].target").value("window"))
                .andExpect(jsonPath("$.decisions[0].action").value("close"));
    }

    private void createRoom(String name) throws Exception {
        createRoomAs("auth0|window-user", name);
    }

    private String createRoomAs(String username, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .with(user(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void connectPhysicalDevice(String roomId, String name, String tbDeviceId) throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/devices/physical", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "tbDeviceId": "%s"
                                }
                                """.formatted(name, tbDeviceId)))
                .andExpect(status().isCreated());
    }

    private void pairPhysicalDevice(String roomId, String name, String pairingCode) throws Exception {
        pairPhysicalDevice(roomId, name, pairingCode, pairingCode.replace("DEMO", "SN"));
    }

    private void pairPhysicalDevice(String roomId, String name, String pairingCode, String serialNumber) throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/devices/pair", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "serialNumber": "%s",
                                  "pairingCode": "%s"
                                }
                                """.formatted(name, serialNumber, pairingCode)))
                .andExpect(status().isCreated());
    }

    private void addVirtualDevice(String roomId, String name) throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/devices/virtual", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated());
    }

    private void insertRegistryDevice(String serialNumber, String pairingCode, String tbDeviceId, String status) {
        jdbcTemplate.update("""
                insert into physical_device_registry (
                  id,
                  serial_number,
                  pairing_code_hash,
                  tb_device_id,
                  status,
                  created_at
                ) values (?, ?, ?, ?, ?, current_timestamp)
                """,
                UUID.randomUUID(),
                serialNumber,
                PhysicalDevicePairingCodeHasher.hash(pairingCode),
                tbDeviceId,
                status
        );
    }

    private void insertRegistryDevice(
            String serialNumber,
            String pairingCode,
            String tbDeviceId,
            String status,
            String thingsBoardAccessToken,
            String capability
    ) {
        UUID registryId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into physical_device_registry (
                  id,
                  serial_number,
                  pairing_code_hash,
                  tb_device_id,
                  status,
                  thingsboard_access_token_hash,
                  capabilities,
                  created_at
                ) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)
                """,
                registryId,
                serialNumber,
                PhysicalDevicePairingCodeHasher.hash(pairingCode),
                tbDeviceId,
                status,
                PhysicalDeviceSecretHasher.hash(thingsBoardAccessToken),
                capability
        );
        jdbcTemplate.update("""
                insert into physical_device_registry_capabilities (
                  physical_device_registry_id,
                  capability
                ) values (?, ?)
                """,
                registryId,
                capability
        );
    }
}
