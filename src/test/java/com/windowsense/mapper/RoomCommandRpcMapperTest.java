package com.windowsense.mapper;

import com.windowsense.entity.RuntimeState;
import com.windowsense.mapper.RoomCommandRpcMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomCommandRpcMapperTest {

    private final RoomCommandRpcMapper mapper = new RoomCommandRpcMapper();

    @Test
    void mapsWindowCommands() {
        assertRpc(command("window", "open", 100), "openWindow", 100);
        assertRpc(command("window", "close", 0), "closeWindow", 0);
        assertRpc(command("window", "setPosition", 42), "setWindowPosition", 42);

        RuntimeState.Command stop = command("window", "stop", null);
        var mapped = mapper.toRpc(stop);
        assertThat(mapped.method()).isEqualTo("stopWindow");
        assertThat(mapped.params()).containsEntry("commandId", stop.id);
        assertThat(mapped.params()).doesNotContainKey("position");
    }

    @Test
    void mapsBlindsCommands() {
        assertRpc(command("blinds", "open", 0), "openBlinds", 0);
        assertRpc(command("blinds", "close", 100), "closeBlinds", 100);
        assertRpc(command("blinds", "setPosition", 85), "setBlindsPosition", 85);

        RuntimeState.Command stop = command("blinds", "stop", null);
        var mapped = mapper.toRpc(stop);
        assertThat(mapped.method()).isEqualTo("stopBlinds");
        assertThat(mapped.params()).containsEntry("commandId", stop.id);
        assertThat(mapped.params()).doesNotContainKey("position");
    }

    private void assertRpc(RuntimeState.Command command, String method, double position) {
        var mapped = mapper.toRpc(command);
        assertThat(mapped.method()).isEqualTo(method);
        assertThat(mapped.params()).containsEntry("commandId", command.id);
        assertThat(mapped.params()).containsEntry("position", position);
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
