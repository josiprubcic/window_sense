package com.windowsense.repository;

import com.windowsense.config.WindowSenseProperties;
import com.windowsense.model.WindowSenseState;
import org.springframework.stereotype.Repository;

import java.util.function.Consumer;
import java.util.function.Function;

@Repository
public class WindowSenseStateRepository {

    private final WindowSenseState state;

    public WindowSenseStateRepository(WindowSenseProperties properties) {
        this.state = WindowSenseState.createDefault(properties.getDeviceId());
    }

    public synchronized WindowSenseState getState() {
        return state;
    }

    public synchronized <T> T withState(Function<WindowSenseState, T> operation) {
        return operation.apply(state);
    }

    public synchronized void update(Consumer<WindowSenseState> operation) {
        operation.accept(state);
    }
}
