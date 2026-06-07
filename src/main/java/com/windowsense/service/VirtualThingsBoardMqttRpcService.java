package com.windowsense.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windowsense.exception.EncryptionException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.entity.WindowDevice;
import com.windowsense.repository.WindowDeviceRepository;
import com.windowsense.security.EncryptionService;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(prefix = "windowsense.virtual-simulator", name = "mqtt-rpc-enabled", havingValue = "true")
public class VirtualThingsBoardMqttRpcService {

    private static final Logger log = LoggerFactory.getLogger(VirtualThingsBoardMqttRpcService.class);
    private static final String RPC_REQUEST_TOPIC = "v1/devices/me/rpc/request/+";
    private static final String RPC_RESPONSE_PREFIX = "v1/devices/me/rpc/response/";
    private static final String TELEMETRY_TOPIC = "v1/devices/me/telemetry";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final WindowDeviceRepository windowDeviceRepository;
    private final EncryptionService encryptionService;
    private final VirtualDeviceRpcHandler rpcHandler;
    private final ObjectMapper objectMapper;
    private final WindowSenseProperties.ThingsBoard thingsBoardProperties;
    private final Map<UUID, MqttClient> clients = new ConcurrentHashMap<>();

    public VirtualThingsBoardMqttRpcService(
            WindowDeviceRepository windowDeviceRepository,
            EncryptionService encryptionService,
            VirtualDeviceRpcHandler rpcHandler,
            ObjectMapper objectMapper,
            WindowSenseProperties properties
    ) {
        this.windowDeviceRepository = windowDeviceRepository;
        this.encryptionService = encryptionService;
        this.rpcHandler = rpcHandler;
        this.objectMapper = objectMapper;
        this.thingsBoardProperties = properties.getThingsBoard();
    }

    @Scheduled(
            initialDelayString = "${windowsense.virtual-simulator.mqtt-reconnect-interval-ms:10000}",
            fixedDelayString = "${windowsense.virtual-simulator.mqtt-reconnect-interval-ms:10000}"
    )
    public void reconcileConnections() {
        if (brokerUri().isBlank()) {
            log.warn("ThingsBoard MQTT host nije konfiguriran; virtualni RPC listener se ne spaja.");
            return;
        }

        Set<UUID> activeDeviceIds = new HashSet<>();
        for (WindowDevice device : windowDeviceRepository.findActiveVirtualDevicesWithRoomAndToken()) {
            activeDeviceIds.add(device.getId());
            MqttClient existing = clients.get(device.getId());
            if (existing != null && existing.isConnected()) {
                continue;
            }
            connect(device);
        }

        for (UUID localDeviceId : new HashSet<>(clients.keySet())) {
            if (!activeDeviceIds.contains(localDeviceId)) {
                disconnect(localDeviceId);
            }
        }
    }

    private void connect(WindowDevice device) {
        String token;
        try {
            token = encryptionService.decrypt(device.getTbDeviceTokenEncrypted());
        } catch (EncryptionException error) {
            log.warn("Virtualni MQTT RPC listener ne moze dekriptirati token za uredjaj {}.", device.getId());
            return;
        }

        try {
            MqttClient client = new MqttClient(
                    brokerUri(),
                    "windowsense-virtual-" + device.getId(),
                    new MemoryPersistence()
            );
            client.setCallback(callback(device.getId()));
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(token);
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            client.connect(options);
            client.subscribe(RPC_REQUEST_TOPIC, 1);
            clients.put(device.getId(), client);
            log.info("Virtualni uredjaj {} spojen je na ThingsBoard MQTT RPC listener.", device.getId());
        } catch (MqttException error) {
            log.warn("Virtualni MQTT RPC listener se nije uspio spojiti za uredjaj {}: {}.",
                    device.getId(),
                    error.getReasonCode());
        }
    }

    private MqttCallbackExtended callback(UUID localDeviceId) {
        return new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                MqttClient client = clients.get(localDeviceId);
                if (client == null || !client.isConnected()) {
                    return;
                }
                try {
                    client.subscribe(RPC_REQUEST_TOPIC, 1);
                } catch (MqttException error) {
                    log.warn("Virtualni MQTT RPC listener nije obnovio subscribe za uredjaj {}.", localDeviceId);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("Virtualni MQTT RPC listener izgubio je vezu za uredjaj {}.", localDeviceId);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                handleRpc(localDeviceId, topic, message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        };
    }

    private void handleRpc(UUID localDeviceId, String topic, MqttMessage message) {
        String requestId = requestId(topic);
        MqttClient client = clients.get(localDeviceId);
        if (client == null || requestId.isBlank()) {
            return;
        }

        Map<String, Object> rpc = parse(message);
        String method = stringValue(rpc.get("method"));
        Map<String, Object> params = params(rpc.get("params"));
        try {
            VirtualDeviceRpcResult result = rpcHandler.handle(localDeviceId, method, params);
            publish(client, RPC_RESPONSE_PREFIX + requestId, result.response());
            publish(client, TELEMETRY_TOPIC, result.telemetry());
        } catch (RuntimeException error) {
            publish(client, RPC_RESPONSE_PREFIX + requestId, Map.of(
                    "status", "FAILED",
                    "error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()
            ));
            log.warn("Virtualni MQTT RPC nije izvrsen za uredjaj {}: {}.",
                    localDeviceId,
                    error.getClass().getSimpleName());
        }
    }

    private Map<String, Object> parse(MqttMessage message) {
        try {
            return objectMapper.readValue(new String(message.getPayload(), StandardCharsets.UTF_8), MAP_TYPE);
        } catch (Exception error) {
            throw new IllegalArgumentException("RPC payload nije valjan JSON.", error);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> params(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> params = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    params.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return params;
        }
        if (raw == null) {
            return Map.of();
        }
        return Map.of("value", raw);
    }

    private void publish(MqttClient client, String topic, Map<String, Object> payload) {
        try {
            client.publish(topic, objectMapper.writeValueAsBytes(payload), 1, false);
        } catch (Exception error) {
            log.warn("Virtualni MQTT RPC listener nije uspio publishati na {}: {}.", topic, error.getClass().getSimpleName());
        }
    }

    private String requestId(String topic) {
        int index = topic == null ? -1 : topic.lastIndexOf('/');
        return index < 0 || index == topic.length() - 1 ? "" : topic.substring(index + 1);
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private String brokerUri() {
        String configured = thingsBoardProperties.getMqttHost();
        if (!configured.isBlank()) {
            return configured.contains("://") ? configured : defaultScheme(configured) + configured;
        }
        String host = thingsBoardProperties.getHost();
        if (host.isBlank()) {
            return "";
        }
        URI uri = URI.create(host);
        String scheme = "https".equalsIgnoreCase(uri.getScheme()) ? "ssl://" : "tcp://";
        int port = "https".equalsIgnoreCase(uri.getScheme()) ? 8883 : 1883;
        return scheme + uri.getHost() + ":" + port;
    }

    private String defaultScheme(String mqttHost) {
        if (mqttHost.contains(":8883") || thingsBoardProperties.getHost().startsWith("https://")) {
            return "ssl://";
        }
        return "tcp://";
    }

    private void disconnect(UUID localDeviceId) {
        MqttClient client = clients.remove(localDeviceId);
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException error) {
            log.warn("Virtualni MQTT RPC listener nije uredno zatvorio vezu za uredjaj {}.", localDeviceId);
        }
    }

    @PreDestroy
    public void shutdown() {
        for (UUID localDeviceId : new HashSet<>(clients.keySet())) {
            disconnect(localDeviceId);
        }
    }
}
