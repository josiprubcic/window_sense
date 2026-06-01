package com.windowsense;

import com.windowsense.config.WindowSenseProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowSensePropertiesTest {

    @Test
    void oidcIssuerUriKeepsAuth0TrailingSlash() {
        WindowSenseProperties.Oidc oidc = new WindowSenseProperties.Oidc();

        oidc.setIssuerUri(" https://example.eu.auth0.com/ ");

        assertThat(oidc.getIssuerUri()).isEqualTo("https://example.eu.auth0.com/");
    }
}
