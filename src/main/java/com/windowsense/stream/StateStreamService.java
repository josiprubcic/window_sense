package com.windowsense.stream;

import com.windowsense.events.StateChangedEvent;
import com.windowsense.model.WindowSenseState;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
public class StateStreamService {

    private final List<Consumer<WindowSenseState>> listeners = new CopyOnWriteArrayList<>();

    public Runnable subscribe(Consumer<WindowSenseState> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @EventListener
    public void onStateChanged(StateChangedEvent event) {
        for (Consumer<WindowSenseState> listener : listeners) {
            listener.accept(event.state());
        }
    }
}
