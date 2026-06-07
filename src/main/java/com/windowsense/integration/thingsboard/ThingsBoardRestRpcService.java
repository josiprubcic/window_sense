package com.windowsense.integration.thingsboard;

import com.windowsense.config.WindowSenseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ThingsBoardRestRpcService implements ThingsBoardRpcService {

    private static final Logger log = LoggerFactory.getLogger(ThingsBoardRestRpcService.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE = new ParameterizedTypeReference<>() {
    };

    private final WindowSenseProperties.ThingsBoard properties;
    private final ThingsBoardAuthClient authClient;
    private final RestClient restClient;

    public ThingsBoardRestRpcService(
            WindowSenseProperties properties,
            ThingsBoardAuthClient authClient,
            RestClient.Builder builder
    ) {
        this.properties = properties.getThingsBoard();
        this.authClient = authClient;
        this.restClient = builder.build();
    }

    @Override
    public ThingsBoardRpcResult sendTwoWayRpc(String tbDeviceId, ThingsBoardRpcRequest request) {
        if (tbDeviceId == null || tbDeviceId.isBlank()) {
            return ThingsBoardRpcResult.failed("ThingsBoard Device ID je obavezan.");
        }

        try {
            Map<String, Object> response = restClient.post()
                    .uri(properties.getHost() + "/api/plugins/rpc/twoway/" + tbDeviceId)
                    .header(ThingsBoardAuthClient.AUTH_HEADER, authClient.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(request))
                    .retrieve()
                    .body(MAP_RESPONSE);

            if (response == null || response.isEmpty()) {
                return ThingsBoardRpcResult.sent(Map.of());
            }

            String status = responseStatus(response);
            return new ThingsBoardRpcResult(status, response);
        } catch (HttpStatusCodeException error) {
            if (error.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value() || error.getStatusCode().value() == 408) {
                return ThingsBoardRpcResult.timeout();
            }
            log.warn("ThingsBoard RPC failed for device {} with HTTP {}.", tbDeviceId, error.getStatusCode().value());
            return ThingsBoardRpcResult.failed(responsePreview(error));
        } catch (ResourceAccessException error) {
            log.warn("ThingsBoard RPC timed out or was not reachable for device {}: {}.", tbDeviceId, error.getMessage());
            return ThingsBoardRpcResult.timeout();
        } catch (RestClientException | IllegalArgumentException error) {
            log.warn("ThingsBoard RPC failed for device {}: {}.", tbDeviceId, error.getClass().getSimpleName());
            return ThingsBoardRpcResult.failed(error.getMessage());
        }
    }

    private Map<String, Object> requestBody(ThingsBoardRpcRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("method", request.method());
        body.put("params", request.params());
        body.put("timeout", request.timeout());
        body.put("persistent", request.persistent());
        return body;
    }

    private String responseStatus(Map<String, Object> response) {
        Object status = response.get("status");
        if (status != null && !status.toString().isBlank()) {
            return status.toString().trim().toUpperCase();
        }
        return "EXECUTED";
    }

    private static String responsePreview(HttpStatusCodeException error) {
        String body = error.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "HTTP " + error.getStatusCode().value();
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }
}
