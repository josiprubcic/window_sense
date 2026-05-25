package com.windowsense.service;

import com.windowsense.events.StateChangedEvent;
import com.windowsense.model.WindowSenseState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class StatePublisher {

    private final ApplicationEventPublisher publisher;

    public StatePublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(WindowSenseState state, String reason) {
        publisher.publishEvent(new StateChangedEvent(state, reason));
    }
}
