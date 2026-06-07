package com.windowsense.service;

import com.windowsense.entity.RuntimeState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class EventLogService {

    public void addEvent(RuntimeState state, String level, String source, String title, String details) {
        state.events.add(0, new RuntimeState.Event(level, source, title, details));
        if (state.events.size() > 80) {
            state.events = new ArrayList<>(state.events.subList(0, 80));
        }
    }
}
