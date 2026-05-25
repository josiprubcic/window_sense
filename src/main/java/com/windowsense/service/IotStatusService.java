package com.windowsense.service;

import com.windowsense.model.WindowSenseState;
import com.windowsense.repository.WindowSenseStateRepository;
import org.springframework.stereotype.Service;

@Service
public class IotStatusService {

    private final WindowSenseStateRepository repository;
    private final StatePublisher statePublisher;

    public IotStatusService(WindowSenseStateRepository repository, StatePublisher statePublisher) {
        this.repository = repository;
        this.statePublisher = statePublisher;
    }

    public void setThingsBoardStatus(String connection, String lastSyncAt, String lastError) {
        repository.update(state -> {
            state.iot.connection = connection;
            state.iot.lastSyncAt = lastSyncAt;
            state.iot.lastError = lastError;
            state.updatedAt = WindowSenseState.now();
        });
        statePublisher.publish(repository.getState(), "thingsboard-status");
    }
}
