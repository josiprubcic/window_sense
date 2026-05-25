package com.windowsense.service;

import com.windowsense.model.WindowSenseState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class EventLogService {

    public void addEvent(WindowSenseState state, String level, String source, String title, String details) {
        state.events.add(0, new WindowSenseState.Event(level, source, title, details));
        if (state.events.size() > 80) {
            state.events = new ArrayList<>(state.events.subList(0, 80));
        }
    }
}
