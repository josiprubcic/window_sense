package com.windowsense;

import com.windowsense.thingsboard.NoOpThingsBoardProvisioningService;
import com.windowsense.thingsboard.ThingsBoardProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "windowsense.security.oidc.enabled=false",
        "windowsense.things-board.provisioning-enabled=false"
})
@ActiveProfiles("test")
class ThingsBoardProvisioningConfigurationTest {

    @Autowired
    private ThingsBoardProvisioningService provisioningService;

    @Test
    void provisioningDisabledUsesNoOpImplementation() {
        assertThat(provisioningService).isInstanceOf(NoOpThingsBoardProvisioningService.class);
    }
}
