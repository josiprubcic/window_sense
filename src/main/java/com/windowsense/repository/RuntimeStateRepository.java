package com.windowsense.repository;

import com.windowsense.entity.RuntimeState;
import org.springframework.stereotype.Repository;

import java.util.function.Function;

@Repository
public class RuntimeStateRepository {

    private final RuntimeState state;

    public RuntimeStateRepository() {
        this.state = RuntimeState.createDefault();
    }

    public synchronized RuntimeState getState() {
        return state;
    }

    public synchronized <T> T withState(Function<RuntimeState, T> operation) {
        return operation.apply(state);
    }
}
