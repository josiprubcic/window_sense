package com.windowsense;

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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "windowsense.security.oidc.enabled=false")
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

    private void createRoom(String name) throws Exception {
        createRoomAs("auth0|window-user", name);
    }

    private void createRoomAs(String username, String name) throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .with(user(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated());
    }
}
