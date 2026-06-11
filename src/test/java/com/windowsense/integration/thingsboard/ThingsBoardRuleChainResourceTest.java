package com.windowsense.integration.thingsboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ThingsBoardRuleChainResourceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SAVE_TIMESERIES_NODE = "org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNode";
    private static final String SEND_RPC_REQUEST_NODE = "org.thingsboard.rule.engine.rpc.TbSendRPCRequestNode";

    @Test
    void exportedRuleChainsSaveTelemetryAndForwardServerRpc() throws IOException {
        assertRuleChainContract("thingsboard/rule-chains/testprozorchain.json");
    }

    private static void assertRuleChainContract(String resourcePath) throws IOException {
        JsonNode root = readResource(resourcePath);
        JsonNode nodes = root.at("/metadata/nodes");
        JsonNode connections = root.at("/metadata/connections");
        int saveTimeseriesIndex = firstNodeIndex(nodes, SAVE_TIMESERIES_NODE);
        int sendRpcIndex = firstNodeIndex(nodes, SEND_RPC_REQUEST_NODE);

        assertThat(saveTimeseriesIndex)
                .as(resourcePath + " contains Save Timeseries node")
                .isNotNegative();
        assertThat(hasPostTelemetryConnectionTo(connections, saveTimeseriesIndex))
                .as(resourcePath + " routes Post telemetry to Save Timeseries")
                .isTrue();
        assertThat(sendRpcIndex)
                .as(resourcePath + " contains Forward RPC to Device node")
                .isNotNegative();
        assertThat(hasConnection(connections, 0, sendRpcIndex, "RPC Request to Device"))
                .as(resourcePath + " routes server-side RPC to RPC call request node")
                .isTrue();
        assertThat(transformScripts(nodes))
                .as(resourcePath + " builds setAngle RPC commands")
                .contains("method: \"setAngle\"")
                .contains("metadata.shared_rainProbability > metadata.shared_desiredRainProbability")
                .contains("metadata.currentWindowOpenPercent = msg.windowOpenPercent")
                .doesNotContain("Hydrate latest telemetry");
        assertThat(hasConnection(connections, 0, 2, "Attributes Updated"))
                .as(resourcePath + " evaluates automation after shared attribute updates")
                .isTrue();
        assertThat(sharedAttributeNames(nodes))
                .as(resourcePath + " reads shared automation attributes")
                .contains("manualMode", "desiredAngleDay", "desiredAngleNight", "desiredAngleRain", "desiredRainProbability", "rainProbability");
    }

    private static JsonNode readResource(String resourcePath) throws IOException {
        try (InputStream input = ThingsBoardRuleChainResourceTest.class
                .getClassLoader()
                .getResourceAsStream(resourcePath)) {
            assertThat(input)
                    .as(resourcePath + " exists")
                    .isNotNull();
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static int firstNodeIndex(JsonNode nodes, String type) {
        for (int i = 0; i < nodes.size(); i++) {
            if (type.equals(nodes.get(i).path("type").asText())) {
                return i;
            }
        }
        return -1;
    }

    private static int nodeIndexByName(JsonNode nodes, String name) {
        for (int i = 0; i < nodes.size(); i++) {
            if (name.equals(nodes.get(i).path("name").asText())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasPostTelemetryConnectionTo(JsonNode connections, int nodeIndex) {
        return hasConnection(connections, 0, nodeIndex, "Post telemetry");
    }

    private static boolean hasConnection(JsonNode connections, int fromIndex, int toIndex, String type) {
        Set<String> acceptedTypes = Set.of(type);
        for (JsonNode connection : connections) {
            if (connection.path("fromIndex").asInt(-1) == fromIndex
                    && connection.path("toIndex").asInt(-1) == toIndex
                    && acceptedTypes.contains(connection.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    private static String transformScripts(JsonNode nodes) {
        StringBuilder scripts = new StringBuilder();
        for (JsonNode node : nodes) {
            JsonNode configuration = node.path("configuration");
            scripts.append(configuration.path("tbelScript").asText()).append('\n');
            scripts.append(configuration.path("jsScript").asText()).append('\n');
        }
        return scripts.toString();
    }

    private static Set<String> sharedAttributeNames(JsonNode nodes) {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (JsonNode node : nodes) {
            for (JsonNode attributeName : node.at("/configuration/sharedAttributeNames")) {
                names.add(attributeName.asText());
            }
        }
        return names;
    }
}
