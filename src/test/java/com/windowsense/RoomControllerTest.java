package com.windowsense;

import com.jayway.jsonpath.JsonPath;
import com.windowsense.device.PhysicalDevicePairingCodeHasher;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "windowsense.security.oidc.enabled=false",
        "windowsense.things-board.provisioning-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from window_device");
        jdbcTemplate.update("delete from physical_device_registry");
        jdbcTemplate.update("delete from room");
        jdbcTemplate.update("delete from home");
        jdbcTemplate.update("delete from app_user");
    }

    @Test
    @WithMockUser(username = "auth0|window-user")
    void createRoomCreatesRoomAndVirtualDevice() throws Exception {
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
                .andExpect(jsonPath("$.devices", hasSize(1)))
                .andExpect(jsonPath("$.devices[0].name").value("WindowSense - Dnevni boravak"))
                .andExpect(jsonPath("$.devices[0].deviceType").value("VIRTUAL"))
                .andExpect(jsonPath("$.devices[0].isVirtual").value(true))
                .andExpect(jsonPath("$.devices[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.devices[0].tbDeviceId").exists());
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
                .andExpect(jsonPath("$.devices", hasSize(2)))
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
                .andExpect(jsonPath("$[0].devices", hasSize(2)));
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
    void connectPhysicalDeviceReturnsConflictWhenRoomAlreadyHasActivePhysicalDevice() throws Exception {
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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Soba vec ima aktivni fizicki uredjaj."));
    }

    @Test
    void pairPhysicalDeviceClaimsAvailableRegistryDeviceForOwnedRoom() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Dnevni boravak");
        insertRegistryDevice("WS-SN-0001", "WS-DEMO-0001", "tb-device-physical-1", "AVAILABLE");

        MvcResult result = mockMvc.perform(post("/api/rooms/{roomId}/devices/pair", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Dnevni boravak",
                                  "pairingCode": "ws-demo-0001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(roomId))
                .andExpect(jsonPath("$.devices", hasSize(2)))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> deviceTypes = JsonPath.read(body, "$.devices[*].deviceType");
        List<Boolean> virtualFlags = JsonPath.read(body, "$.devices[*].isVirtual");
        List<String> tbDeviceIds = JsonPath.read(body, "$.devices[*].tbDeviceId");
        assertThat(deviceTypes).contains("PHYSICAL");
        assertThat(virtualFlags).contains(false);
        assertThat(tbDeviceIds).contains("tb-device-physical-1");

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
    }

    @Test
    void pairPhysicalDeviceReturnsNotFoundForInvalidPairingCode() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Dnevni boravak");

        mockMvc.perform(post("/api/rooms/{roomId}/devices/pair", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Dnevni boravak",
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
                                  "pairingCode": "WS-DEMO-0002"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Uredjaj je vec povezan."));
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
                                  "pairingCode": "WS-DEMO-0003"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Uredjaj je deaktiviran."));
    }

    @Test
    void pairPhysicalDeviceReturnsNotFoundForForeignRoom() throws Exception {
        String roomId = createRoomAs("auth0|first-user", "Dnevni boravak");
        insertRegistryDevice("WS-SN-0004", "WS-DEMO-0004", "tb-device-physical-4", "AVAILABLE");

        mockMvc.perform(post("/api/rooms/{roomId}/devices/pair", roomId)
                        .with(user("auth0|second-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Dnevni boravak",
                                  "pairingCode": "WS-DEMO-0004"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Soba nije pronadjena."));

        assertThat(jdbcTemplate.queryForObject(
                "select status from physical_device_registry where serial_number = ?",
                String.class,
                "WS-SN-0004"
        )).isEqualTo("AVAILABLE");
    }

    @Test
    void pairPhysicalDeviceReturnsConflictWhenRoomAlreadyHasActivePhysicalDevice() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Dnevni boravak");
        insertRegistryDevice("WS-SN-0005", "WS-DEMO-0005", "tb-device-physical-5", "AVAILABLE");
        insertRegistryDevice("WS-SN-0006", "WS-DEMO-0006", "tb-device-physical-6", "AVAILABLE");
        pairPhysicalDevice(roomId, "ESP32 - Prvi", "WS-DEMO-0005");

        mockMvc.perform(post("/api/rooms/{roomId}/devices/pair", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ESP32 - Drugi",
                                  "pairingCode": "WS-DEMO-0006"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Soba vec ima aktivni fizicki uredjaj."));

        assertThat(jdbcTemplate.queryForObject(
                "select status from physical_device_registry where serial_number = ?",
                String.class,
                "WS-SN-0006"
        )).isEqualTo("AVAILABLE");
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
                .andExpect(jsonPath("$.devices", hasSize(1)))
                .andExpect(jsonPath("$.devices[0].deviceType").value("VIRTUAL"))
                .andExpect(jsonPath("$.devices[0].isVirtual").value(true));
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
    void latestTelemetryForOwnedMockRoomReturnsEmptyResponse() throws Exception {
        String roomId = createRoomAs("auth0|window-user", "Kuhinja");

        mockMvc.perform(get("/api/rooms/{roomId}/telemetry/latest", roomId)
                        .with(user("auth0|window-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.roomName").value("Kuhinja"))
                .andExpect(jsonPath("$.telemetry").isEmpty())
                .andExpect(jsonPath("$.message").value("Telemetrija jos nije dostupna za mock ThingsBoard uredjaj."));
    }

    @Test
    void latestTelemetryReturnsNotFoundForForeignRoom() throws Exception {
        String roomId = createRoomAs("auth0|first-user", "Kuhinja");

        mockMvc.perform(get("/api/rooms/{roomId}/telemetry/latest", roomId)
                        .with(user("auth0|second-user")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Soba nije pronadjena."));
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
        mockMvc.perform(post("/api/rooms/{roomId}/devices/pair", roomId)
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "pairingCode": "%s"
                                }
                                """.formatted(name, pairingCode)))
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
}
