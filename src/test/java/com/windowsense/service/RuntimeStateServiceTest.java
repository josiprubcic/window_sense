package com.windowsense.service;

import com.windowsense.entity.RuntimeState;
import com.windowsense.repository.RuntimeStateRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeStateServiceTest {

    @Test
    @Tag("core")
    void eventsCanBeFilteredByRoomId() {
        RuntimeStateRepository repository = new RuntimeStateRepository();
        RuntimeStateService service = new RuntimeStateService(repository);
        repository.withState(state -> {
            state.events.clear();
            state.events.add(new RuntimeState.Event(
                    "info",
                    "thingsboard-rpc",
                    "Dnevna",
                    "Event iz dnevne",
                    "room-1",
                    "Dnevna",
                    "device-1",
                    "Prozor",
                    "Rule chain"
            ));
            state.events.add(new RuntimeState.Event(
                    "info",
                    "thingsboard-rpc",
                    "Spavaca",
                    "Event iz spavace",
                    "room-2",
                    "Spavaca",
                    "device-2",
                    "Roleta",
                    "Rule chain"
            ));
            return null;
        });

        var roomEvents = service.events("room-1");
        assertThat(roomEvents).hasSize(1);
        assertThat(roomEvents.getFirst().roomName).isEqualTo("Dnevna");
        assertThat(roomEvents.getFirst().details).isEqualTo("Event iz dnevne");
        assertThat(roomEvents.getFirst().reason).isEqualTo("Rule chain");
        assertThat(service.events()).hasSize(2);
    }
}
