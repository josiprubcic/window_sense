package com.windowsense.controller;

import com.windowsense.exception.ThingsBoardProvisioningException;
import com.windowsense.entity.WindowDevice;
import com.windowsense.entity.Home;
import com.windowsense.repository.HomeRepository;
import com.windowsense.entity.Room;
import com.windowsense.repository.RoomRepository;
import com.windowsense.integration.thingsboard.RoomDeviceDeprovisioningRequest;
import com.windowsense.integration.thingsboard.ThingsBoardProvisioningService;
import com.windowsense.entity.AppUser;
import com.windowsense.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "windowsense.security.oidc.enabled=false",
        "windowsense.things-board.provisioning-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomControllerDeprovisionFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private HomeRepository homeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @MockBean
    private ThingsBoardProvisioningService provisioningService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from window_device");
        jdbcTemplate.update("delete from room");
        jdbcTemplate.update("delete from home");
        jdbcTemplate.update("delete from app_user");
    }

    @Test
    void deleteRoomReturnsBadGatewayAndKeepsLocalRoomWhenDeprovisioningFails() throws Exception {
        UUID roomId = createPersistedRoomWithRealThingsBoardIds();
        doThrow(new ThingsBoardProvisioningException("ThingsBoard deprovisioning nije uspio."))
                .when(provisioningService)
                .deprovisionRoomDevice(any(RoomDeviceDeprovisioningRequest.class));

        mockMvc.perform(delete("/api/rooms/{roomId}", roomId)
                        .with(user("auth0|window-user")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("ThingsBoard deprovisioning nije uspio."));

        mockMvc.perform(delete("/api/rooms/{roomId}", roomId)
                        .with(user("auth0|other-user")))
                .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(roomRepository.findById(roomId)).isPresent();
    }

    private UUID createPersistedRoomWithRealThingsBoardIds() {
        AppUser appUser = appUserRepository.save(new AppUser(
                "auth0|window-user",
                "window-user@example.com",
                "Window User"
        ));
        Home home = homeRepository.save(new Home(appUser, "Default Home"));
        Room room = new Room(home, "Kuhinja", "real-asset-id");
        room.addDevice(WindowDevice.virtualDevice("WindowSense - Kuhinja", "real-device-id"));
        return roomRepository.saveAndFlush(room).getId();
    }
}
