package com.windowsense.iot;

import com.windowsense.model.WindowSenseState;
import com.windowsense.store.WindowSenseStore;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ThingsBoardSyncService {

    private final WindowSenseStore store;
    private final ThingsBoardClient thingsBoardClient;
    private final AtomicBoolean updatingSyncStatus = new AtomicBoolean(false);

    public ThingsBoardSyncService(WindowSenseStore store, ThingsBoardClient thingsBoardClient) {
        this.store = store;
        this.thingsBoardClient = thingsBoardClient;
    }

    @PostConstruct
    public void init() {
        Map<String, Object> status = thingsBoardClient.status();
        store.setThingsBoardStatus(
                status.get("connection").toString(),
                blankToNull(status.get("lastSyncAt")),
                blankToNull(status.get("lastError"))
        );
        store.subscribe(this::sync);
    }

    private void sync(WindowSenseState state) {
        if (updatingSyncStatus.get() || !thingsBoardClient.isReady()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                thingsBoardClient.sendTelemetry(state);
                updateStatus("connected", WindowSenseState.now(), null);
            } catch (RuntimeException error) {
                updateStatus("error", null, error.getMessage());
            }
        });
    }

    private void updateStatus(String connection, String lastSyncAt, String lastError) {
        updatingSyncStatus.set(true);
        try {
            store.setThingsBoardStatus(connection, lastSyncAt, lastError);
        } finally {
            updatingSyncStatus.set(false);
        }
    }

    private static String blankToNull(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }

        return value.toString();
    }
}
