package com.windowsense.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class WindowSenseState {

    public Site site = new Site();
    public Sensors sensors = new Sensors();
    public Weather weather = new Weather();
    public Actuators actuators = new Actuators();
    public Automation automation = new Automation();
    public Iot iot = new Iot();
    public List<Command> commandQueue = new ArrayList<>();
    public List<Event> events = new ArrayList<>();
    public String updatedAt = now();

    public static WindowSenseState createDefault(String deviceId) {
        WindowSenseState state = new WindowSenseState();
        String now = now();

        state.site.name = "WindowSense Lab";
        state.site.area = "Pametna ucionica";
        state.site.deviceId = deviceId;

        state.sensors.rainDetected = false;
        state.sensors.rainIntensity = 0;
        state.sensors.lightLux = 42000;
        state.sensors.windowContactOpen = true;
        state.sensors.indoorTempC = 24.8;
        state.sensors.outdoorTempC = 20.9;
        state.sensors.batteryPercent = 94;
        state.sensors.signalStrength = -58;

        state.weather.condition = "Djelomicno suncano";
        state.weather.rainProbability = 18;
        state.weather.windKph = 12;
        state.weather.forecastSource = "simulated";
        state.weather.updatedAt = now;

        state.actuators.window.openPercent = 65;
        state.actuators.blinds.positionPercent = 30;

        state.events.add(new Event("info", "system", "Sustav spreman",
                "Pokrenut je WindowSense Spring Boot backend s lokalnim simuliranim stanjem."));
        state.updatedAt = now;
        return state;
    }

    public static String now() {
        return Instant.now().toString();
    }

    public static class Site {
        public String name;
        public String area;
        public String deviceId;
    }

    public static class Sensors {
        public boolean rainDetected;
        public double rainIntensity;
        public double lightLux;
        public boolean windowContactOpen;
        public double indoorTempC;
        public double outdoorTempC;
        public double batteryPercent;
        public double signalStrength;
    }

    public static class Weather {
        public String condition;
        public double rainProbability;
        public double windKph;
        public String forecastSource;
        public String updatedAt;
    }

    public static class Actuators {
        public WindowActuator window = new WindowActuator();
        public BlindsActuator blinds = new BlindsActuator();
    }

    public static class DeviceActuator {
        public String status = "idle";
        public String lastCommandAt;
    }

    public static class WindowActuator extends DeviceActuator {
        public double openPercent;
    }

    public static class BlindsActuator extends DeviceActuator {
        public double positionPercent;
    }

    public static class Automation {
        public String mode = "auto";
        public String lastDecisionAt;
        public Thresholds thresholds = new Thresholds();
    }

    public static class Thresholds {
        public double rainIntensityClose = 0;
        public double rainProbabilityClose = 55;
        public double windKphClose = 45;
        public double lightLuxShade = 55000;
        public double lightLuxRelease = 16000;
        public double indoorTempShadeC = 25;
        public double blindsShadePosition = 85;
        public double blindsReleasePosition = 20;
    }

    public static class Iot {
        public String platform = "ThingsBoard";
        public String connection = "not_configured";
        public String lastSyncAt;
        public String lastError;
    }

    public static class Event {
        public String id;
        public String ts;
        public String level;
        public String source;
        public String title;
        public String details;

        public Event() {
        }

        public Event(String level, String source, String title, String details) {
            this.id = Ids.eventId();
            this.ts = now();
            this.level = level;
            this.source = source;
            this.title = title;
            this.details = details;
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
