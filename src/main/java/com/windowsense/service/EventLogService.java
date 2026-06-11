package com.windowsense.service;

import com.windowsense.entity.RuntimeState;
import com.windowsense.entity.Room;
import com.windowsense.entity.WindowDevice;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class EventLogService {

    public void addEvent(RuntimeState state, String level, String source, String title, String details) {
        addEvent(state, level, source, title, details, null, null, null, null, null);
    }

    public void addRoomEvent(
            RuntimeState state,
            String level,
            String source,
            String title,
            String details,
            Room room,
            WindowDevice device,
            String reason
    ) {
        addEvent(
                state,
                level,
                source,
                title,
                details,
                room == null || room.getId() == null ? null : room.getId().toString(),
                room == null ? null : room.getName(),
                device == null || device.getId() == null ? null : device.getId().toString(),
                device == null ? null : device.getName(),
                reason
        );
    }

    public void addEvent(
            RuntimeState state,
            String level,
            String source,
            String title,
            String details,
            String roomId,
            String roomName,
            String deviceId,
            String deviceName,
            String reason
    ) {
        state.events.add(0, new RuntimeState.Event(level, source, title, details, roomId, roomName, deviceId, deviceName, reason));
        if (state.events.size() > 80) {
            state.events = new ArrayList<>(state.events.subList(0, 80));
        }
    }
}
