package com.windowsense.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RuntimeState {

    public List<Command> commandQueue = new ArrayList<>();
    public List<Event> events = new ArrayList<>();
    public String updatedAt = now();

    public static RuntimeState createDefault() {
        RuntimeState state = new RuntimeState();
        state.events.add(new Event(
                "info",
                "system",
                "Sustav spreman",
                "Pokrenut je WindowSense backend s room-first modelom."
        ));
        state.updatedAt = now();
        return state;
    }

    public static String now() {
        return Instant.now().toString();
    }

    public static class Event {
        public String id;
        public String ts;
        public String level;
        public String source;
        public String title;
        public String details;
        public String roomId;
        public String roomName;
        public String deviceId;
        public String deviceName;
        public String reason;

        public Event() {
        }

        public Event(String level, String source, String title, String details) {
            this(level, source, title, details, null, null, null, null, null);
        }

        public Event(
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
            this.id = Ids.eventId();
            this.ts = now();
            this.level = level;
            this.source = source;
            this.title = title;
            this.details = details;
            this.roomId = roomId;
            this.roomName = roomName;
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.reason = reason;
        }
    }

    public static class Command {
        public String id;
        public String ts;
        public String deviceId;
        public String target;
        public String action;
        public Double positionPercent;
        public String source;
        public String status = "pending";
        public String acknowledgedAt;

        public Command() {
        }

        public Command(String deviceId, String target, String action, Double positionPercent, String source) {
            this.id = Ids.commandId();
            this.ts = now();
            this.deviceId = deviceId;
            this.target = target;
            this.action = action;
            this.positionPercent = positionPercent;
            this.source = source;
        }
    }

    private static class Ids {
        private static String eventId() {
            return "evt-" + Long.toString(System.currentTimeMillis(), 36) + "-" + randomSuffix();
        }

        private static String commandId() {
            return "cmd-" + Long.toString(System.currentTimeMillis(), 36) + "-" + randomSuffix();
        }

        private static String randomSuffix() {
            return Long.toString(Double.doubleToLongBits(Math.random()), 36).substring(0, 6);
        }
    }
}
