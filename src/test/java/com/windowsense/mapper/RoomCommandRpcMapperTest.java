package com.windowsense.mapper;

import com.windowsense.entity.RuntimeState;
import com.windowsense.mapper.RoomCommandRpcMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("core")
class RoomCommandRpcMapperTest {

    private final RoomCommandRpcMapper mapper = new RoomCommandRpcMapper();

    @Test
    void mapsWindowCommands() {
        assertRpcWithPosition(command("window", "open", null), "setAngle", 90);
        assertRpcWithPosition(command("window", "close", null), "setAngle", 0);
        assertRpcWithPosition(command("window", "setPosition", 50), "setAngle", 45);

        RuntimeState.Command stop = command("window", "stop", null);
        var mapped = mapper.toRpc(stop);
        assertThat(mapped.method()).isEqualTo("stopWindow");
        assertThat(mapped.params()).isEqualTo(Map.of());
    }

    @Test
    void mapsBlindsCommands() {
        assertRpcWithPosition(command("blinds", "open", null), "setAngle", 0);
        assertRpcWithPosition(command("blinds", "close", null), "setAngle", 90);
        assertRpcWithPosition(command("blinds", "setPosition", 85), "setAngle", 76.5);

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
