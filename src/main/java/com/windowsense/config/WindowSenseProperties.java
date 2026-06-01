package com.windowsense.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "windowsense")
public class WindowSenseProperties {

    private String deviceId = "windowsense-esp32-01";
    private ThingsBoard thingsBoard = new ThingsBoard();
    private Security security = new Security();

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public ThingsBoard getThingsBoard() {
        return thingsBoard;
    }

    public void setThingsBoard(ThingsBoard thingsBoard) {
        this.thingsBoard = thingsBoard;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security == null ? new Security() : security;
    }

    public static class ThingsBoard {
        private String host = "";
        private String accessToken = "";
        private boolean syncEnabled = false;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host == null ? "" : host.replaceAll("/+$", "");
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken == null ? "" : accessToken;
        }

        public boolean isSyncEnabled() {
            return syncEnabled;
        }

        public void setSyncEnabled(boolean syncEnabled) {
            this.syncEnabled = syncEnabled;
        }

        public boolean isReady() {
            return syncEnabled && !host.isBlank() && !accessToken.isBlank();
        }
    }

    public static class Security {
        private Oidc oidc = new Oidc();

        public Oidc getOidc() {
            return oidc;
        }

        public void setOidc(Oidc oidc) {
            this.oidc = oidc == null ? new Oidc() : oidc;
        }
    }

    public static class Oidc {
        private boolean enabled = false;
        private String issuerUri = "";
        private String clientId = "";
        private String clientSecret = "";
        private List<String> scopes = new ArrayList<>(List.of("openid", "profile", "email"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri == null ? "" : issuerUri.trim();
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId == null ? "" : clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret == null ? "" : clientSecret;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(List<String> scopes) {
            this.scopes = scopes == null || scopes.isEmpty()
                    ? new ArrayList<>(List.of("openid", "profile", "email"))
                    : new ArrayList<>(scopes);
        }
    }
}
