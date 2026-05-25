package com.windowsense.iot;

import com.windowsense.events.StateChangedEvent;
import com.windowsense.model.WindowSenseState;
import com.windowsense.service.IotStatusService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class ThingsBoardSyncService {

    private final IotStatusService iotStatusService;
    private final ThingsBoardClient thingsBoardClient;

    public ThingsBoardSyncService(
            IotStatusService iotStatusService,
            ThingsBoardClient thingsBoardClient
    ) {
        this.iotStatusService = iotStatusService;
        this.thingsBoardClient = thingsBoardClient;
    }

    @PostConstruct
    public void init() {
        Map<String, Object> status = thingsBoardClient.status();
        iotStatusService.setThingsBoardStatus(
                status.get("connection").toString(),
                blankToNull(status.get("lastSyncAt")),
                blankToNull(status.get("lastError"))
        );
    }

    @EventListener
    public void onStateChanged(StateChangedEvent event) {
        if ("thingsboard-status".equals(event.reason()) || !thingsBoardClient.isReady()) {
            return;
        }

        sync(event.state());
    }

    private void sync(WindowSenseState state) {
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
        iotStatusService.setThingsBoardStatus(connection, lastSyncAt, lastError);
    }

    private static String blankToNull(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }

        return value.toString();
    }
}
