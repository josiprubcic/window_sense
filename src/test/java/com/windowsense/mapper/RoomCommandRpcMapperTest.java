package com.windowsense.mapper;

import com.windowsense.entity.RuntimeState;
import com.windowsense.mapper.RoomCommandRpcMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoomCommandRpcMapperTest {

    private final RoomCommandRpcMapper mapper = new RoomCommandRpcMapper();

    @Test
    void mapsWindowCommands() {
        assertRpcNoParams(command("window", "open", null), "openWindow");
        assertRpcNoParams(command("window", "close", null), "closeWindow");
        assertRpcWithPosition(command("window", "setPosition", 42), "setWindowPosition", 42);

        RuntimeState.Command stop = command("window", "stop", null);
        var mapped = mapper.toRpc(stop);
        assertThat(mapped.method()).isEqualTo("stopWindow");
        assertThat(mapped.params()).isEqualTo(Map.of());
    }

    @Test
    void mapsBlindsCommands() {
        assertRpcNoParams(command("blinds", "open", null), "openBlinds");
        assertRpcNoParams(command("blinds", "close", null), "closeBlinds");
        assertRpcWithPosition(command("blinds", "setPosition", 85), "setAngle", 85);

        RuntimeState.Command stop = command("blinds", "stop", null);
        var mapped = mapper.toRpc(stop);
        assertThat(mapped.method()).isEqualTo("stopBlinds");
        assertThat(mapped.params()).isEqualTo(Map.of());
    }

    private void assertRpcWithPosition(RuntimeState.Command command, String method, double position) {
        var mapped = mapper.toRpc(command);
        assertThat(mapped.method()).isEqualTo(method);
        assertThat(mapped.params()).isEqualTo(position);
    }

    private void assertRpcNoParams(RuntimeState.Command command, String method) {
        var mapped = mapper.toRpc(command);
        assertThat(mapped.method()).isEqualTo(method);
        assertThat(mapped.params()).isEqualTo(Map.of());
    }

    private RuntimeState.Command command(String target, String action, Integer position) {
        return new RuntimeState.Command(
                "tb-device-id",
                target,
                action,
                position == null ? null : position.doubleValue(),
                "test"
        );
    }
}
