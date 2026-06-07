package com.windowsense.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "windowsense")
public class WindowSenseProperties {

    private ThingsBoard thingsBoard = new ThingsBoard();
    private Security security = new Security();
    private Encryption encryption = new Encryption();
    private VirtualSimulator virtualSimulator = new VirtualSimulator();
    private Commands commands = new Commands();

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

    public Encryption getEncryption() {
        return encryption;
    }

    public void setEncryption(Encryption encryption) {
        this.encryption = encryption == null ? new Encryption() : encryption;
    }

    public VirtualSimulator getVirtualSimulator() {
        return virtualSimulator;
    }

    public void setVirtualSimulator(VirtualSimulator virtualSimulator) {
        this.virtualSimulator = virtualSimulator == null ? new VirtualSimulator() : virtualSimulator;
    }

    public Commands getCommands() {
        return commands;
    }

    public void setCommands(Commands commands) {
        this.commands = commands == null ? new Commands() : commands;
    }

    public static class ThingsBoard {
        private String host = "";
        private String mqttHost = "";
        private boolean provisioningEnabled = false;
        private ProvisioningAuthMode provisioningAuthMode = ProvisioningAuthMode.PASSWORD;
        private ThingsBoardDeleteMode deleteMode = ThingsBoardDeleteMode.SOFT;
        private String username = "";
        private String password = "";
        private String jwtToken = "";
        private String apiKey = "";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host == null ? "" : host.replaceAll("/+$", "");
        }

        public String getMqttHost() {
            return mqttHost;
        }

        public void setMqttHost(String mqttHost) {
            this.mqttHost = mqttHost == null ? "" : mqttHost.trim();
        }

        public boolean isProvisioningEnabled() {
            return provisioningEnabled;
        }

        public void setProvisioningEnabled(boolean provisioningEnabled) {
            this.provisioningEnabled = provisioningEnabled;
        }

        public ProvisioningAuthMode getProvisioningAuthMode() {
            return provisioningAuthMode;
        }

        public void setProvisioningAuthMode(ProvisioningAuthMode provisioningAuthMode) {
            this.provisioningAuthMode = provisioningAuthMode == null ? ProvisioningAuthMode.PASSWORD : provisioningAuthMode;
        }

        public ThingsBoardDeleteMode getDeleteMode() {
            return deleteMode;
        }

        public void setDeleteMode(ThingsBoardDeleteMode deleteMode) {
            this.deleteMode = deleteMode == null ? ThingsBoardDeleteMode.SOFT : deleteMode;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username == null ? "" : username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password == null ? "" : password;
        }

        public String getJwtToken() {
            return jwtToken;
        }

        public void setJwtToken(String jwtToken) {
            this.jwtToken = jwtToken == null ? "" : jwtToken;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey;
        }

        public boolean isProvisioningReady() {
            return provisioningEnabled && isRestAuthReady();
        }

        public boolean isRestAuthReady() {
            return !host.isBlank() && switch (provisioningAuthMode) {
                case PASSWORD -> !username.isBlank() && !password.isBlank();
                case JWT -> !jwtToken.isBlank();
                case API_KEY -> !apiKey.isBlank();
            };
        }
    }

    public enum ProvisioningAuthMode {
        PASSWORD,
        JWT,
        API_KEY
    }

    public enum ThingsBoardDeleteMode {
        SOFT,
        HARD
    }

    public static class Commands {
        private PhysicalCommandDelivery physicalDelivery = PhysicalCommandDelivery.POLLING;
        private Rpc rpc = new Rpc();

        public PhysicalCommandDelivery getPhysicalDelivery() {
            return physicalDelivery;
        }

        public void setPhysicalDelivery(PhysicalCommandDelivery physicalDelivery) {
            this.physicalDelivery = physicalDelivery == null ? PhysicalCommandDelivery.POLLING : physicalDelivery;
        }

        public Rpc getRpc() {
            return rpc;
        }

        public void setRpc(Rpc rpc) {
            this.rpc = rpc == null ? new Rpc() : rpc;
        }
    }

    public enum PhysicalCommandDelivery {
        POLLING,
        THINGSBOARD_RPC
    }

    public static class Rpc {
        private boolean enabled = false;
        private long timeoutMs = 15000;
        private boolean persistent = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs <= 0 ? 15000 : timeoutMs;
        }

        public boolean isPersistent() {
            return persistent;
        }

        public void setPersistent(boolean persistent) {
            this.persistent = persistent;
        }
    }

    public static class Encryption {
        private String key = "";

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key == null ? "" : key.trim();
        }
    }

    public static class VirtualSimulator {
        private boolean enabled = false;
        private boolean publishToThingsBoard = false;
        private boolean mqttRpcEnabled = false;
        private long mqttReconnectIntervalMs = 10000;
        private long intervalMs = 5000;
        private String rainStateFilePath = "./stanje_kise.txt";
        private String weatherDataFilePath = "./vrijeme.csv";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPublishToThingsBoard() {
            return publishToThingsBoard;
        }

        public void setPublishToThingsBoard(boolean publishToThingsBoard) {
            this.publishToThingsBoard = publishToThingsBoard;
        }

        public boolean isMqttRpcEnabled() {
            return mqttRpcEnabled;
        }

        public void setMqttRpcEnabled(boolean mqttRpcEnabled) {
            this.mqttRpcEnabled = mqttRpcEnabled;
        }

        public long getMqttReconnectIntervalMs() {
            return mqttReconnectIntervalMs;
        }

        public void setMqttReconnectIntervalMs(long mqttReconnectIntervalMs) {
            this.mqttReconnectIntervalMs = mqttReconnectIntervalMs <= 0 ? 10000 : mqttReconnectIntervalMs;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs <= 0 ? 5000 : intervalMs;
        }

        public String getRainStateFilePath() {
            return rainStateFilePath;
        }

        public void setRainStateFilePath(String rainStateFilePath) {
            this.rainStateFilePath = rainStateFilePath == null || rainStateFilePath.isBlank()
                    ? "./stanje_kise.txt"
                    : rainStateFilePath.trim();
        }

        public String getWeatherDataFilePath() {
            return weatherDataFilePath;
        }

        public void setWeatherDataFilePath(String weatherDataFilePath) {
            this.weatherDataFilePath = weatherDataFilePath == null || weatherDataFilePath.isBlank()
                    ? "./vrijeme.csv"
                    : weatherDataFilePath.trim();
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
