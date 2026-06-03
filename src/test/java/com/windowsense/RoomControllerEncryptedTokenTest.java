package com.windowsense;

import com.windowsense.security.EncryptionService;
import com.windowsense.thingsboard.ProvisionedRoomDevice;
import com.windowsense.thingsboard.ThingsBoardProvisioningService;
import com.windowsense.thingsboard.VirtualRoomProvisioningRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "windowsense.security.oidc.enabled=false",
        "windowsense.things-board.provisioning-enabled=false",
        "windowsense.encryption.key=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomControllerEncryptedTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EncryptionService encryptionService;

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
    void createRoomStoresEncryptedDeviceAccessTokenWithoutReturningToken() throws Exception {
        when(provisioningService.provisionVirtualRoomDevice(any(VirtualRoomProvisioningRequest.class)))
                .thenReturn(new ProvisionedRoomDevice("real-asset-id", "real-device-id", "plain-device-token"));

        mockMvc.perform(post("/api/rooms")
                        .with(user("auth0|window-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Kuhinja"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.devices[0].tbDeviceId").value("real-device-id"))
                .andExpect(jsonPath("$.devices[0].tbDeviceToken").doesNotExist())
                .andExpect(jsonPath("$.devices[0].tbDeviceAccessToken").doesNotExist())
                .andExpect(jsonPath("$.devices[0].tbDeviceTokenEncrypted").doesNotExist());

        String encryptedToken = jdbcTemplate.queryForObject(
                "select tb_device_token_encrypted from window_device where tb_device_id = ?",
                String.class,
                "real-device-id"
        );
        assertThat(encryptedToken).startsWith("v1:");
        assertThat(encryptedToken).isNotEqualTo("plain-device-token");
        assertThat(encryptionService.decrypt(encryptedToken)).isEqualTo("plain-device-token");
    }
}
