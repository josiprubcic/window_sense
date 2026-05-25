package com.windowsense.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "windowsense")
public class WindowSenseProperties {

    private String deviceId = "windowsense-esp32-01";
    private ThingsBoard thingsBoard = new ThingsBoard();

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
}
