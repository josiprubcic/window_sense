package com.windowsense.store;

import com.windowsense.automation.AutomationService;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.model.CommandRequest;
import com.windowsense.model.CommandResult;
import com.windowsense.model.Decision;
import com.windowsense.model.TelemetryResult;
import com.windowsense.model.WindowSenseState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
public class WindowSenseStore {

    private static final Set<String> TARGETS = Set.of("window", "blinds", "automation");
    private static final Set<String> ACTIONS = Set.of("open", "close", "stop", "setPosition", "auto", "manual");

    private final String deviceId;
    private final AutomationService automationService;
    private final List<Consumer<WindowSenseState>> listeners = new CopyOnWriteArrayList<>();
    private final WindowSenseState state;

    public WindowSenseStore(WindowSenseProperties properties, AutomationService automationService) {
        this.deviceId = properties.getDeviceId();
        this.automationService = automationService;
        this.state = WindowSenseState.createDefault(deviceId);
    }

    public synchronized WindowSenseState getState() {
        return state;
    }

    public Runnable subscribe(Consumer<WindowSenseState> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public synchronized TelemetryResult ingestTelemetry(Map<String, Object> payload, String source) {
        state.sensors.rainDetected = booleanValue(payload, "rainDetected", state.sensors.rainDetected);
        state.sensors.rainIntensity = numberValue(payload, "rainIntensity", state.sensors.rainIntensity, 0, 100);
        state.sensors.lightLux = numberValue(payload, "lightLux", state.sensors.lightLux, 0, 120000);
        state.sensors.windowContactOpen = booleanValue(payload, "windowContactOpen", state.sensors.windowContactOpen);
        state.sensors.indoorTempC = numberValue(payload, "indoorTempC", state.sensors.indoorTempC, -30, 80);
        state.sensors.outdoorTempC = numberValue(payload, "outdoorTempC", state.sensors.outdoorTempC, -40, 80);
        state.sensors.batteryPercent = numberValue(payload, "batteryPercent", state.sensors.batteryPercent, 0, 100);
        state.sensors.signalStrength = numberValue(payload, "signalStrength", state.sensors.signalStrength, -120, 0);

        if (payload.containsKey("windowOpenPercent")) {
            state.actuators.window.openPercent = numberValue(payload, "windowOpenPercent", state.actuators.window.openPercent, 0, 100);
            state.sensors.windowContactOpen = state.actuators.window.openPercent > 0;
        }

        if (payload.containsKey("blindsPositionPercent")) {
            state.actuators.blinds.positionPercent = numberValue(
                    payload,
                    "blindsPositionPercent",
                    state.actuators.blinds.positionPercent,
                    0,
                    100
            );
        }

        if (payload.containsKey("rainProbability")) {
            state.weather.rainProbability = numberValue(payload, "rainProbability", state.weather.rainProbability, 0, 100);
        }

        if (payload.containsKey("windKph")) {
            state.weather.windKph = numberValue(payload, "windKph", state.weather.windKph, 0, 250);
        }

        if (payload.get("condition") instanceof String condition && !condition.isBlank()) {
            state.weather.condition = limit(condition, 80);
        }

        touchWeather();
        addEvent("info", source, "Zaprimljena telemetrija", "Senzorsko stanje je azurirano.");
        List<Decision> decisions = runAutomation(false);
        notifyListeners();
        return new TelemetryResult(state, decisions);
    }

    public synchronized TelemetryResult updateWeather(Map<String, Object> payload) {
        if (payload.get("condition") instanceof String condition && !condition.isBlank()) {
            state.weather.condition = limit(condition, 80);
        }

        state.weather.rainProbability = numberValue(payload, "rainProbability", state.weather.rainProbability, 0, 100);
        state.weather.windKph = numberValue(payload, "windKph", state.weather.windKph, 0, 250);
        state.weather.forecastSource = payload.get("forecastSource") instanceof String source && !source.isBlank()
                ? limit(source, 80)
                : "api";
        touchWeather();
        addEvent("info", "weather", "Prognoza azurirana",
                state.weather.condition + ", " + Math.round(state.weather.rainProbability) + "% rizika kise.");
        List<Decision> decisions = runAutomation(false);
        notifyListeners();
        return new TelemetryResult(state, decisions);
    }

    public synchronized TelemetryResult updateThresholds(Map<String, Object> payload) {
        WindowSenseState.Thresholds thresholds = state.automation.thresholds;
        thresholds.rainIntensityClose = threshold(payload, "rainIntensityClose", thresholds.rainIntensityClose, 0, 100);
        thresholds.rainProbabilityClose = threshold(payload, "rainProbabilityClose", thresholds.rainProbabilityClose, 0, 100);
        thresholds.windKphClose = threshold(payload, "windKphClose", thresholds.windKphClose, 0, 250);
        thresholds.lightLuxShade = threshold(payload, "lightLuxShade", thresholds.lightLuxShade, 0, 120000);
        thresholds.lightLuxRelease = threshold(payload, "lightLuxRelease", thresholds.lightLuxRelease, 0, 120000);
        thresholds.indoorTempShadeC = threshold(payload, "indoorTempShadeC", thresholds.indoorTempShadeC, -30, 80);
        thresholds.blindsShadePosition = threshold(payload, "blindsShadePosition", thresholds.blindsShadePosition, 0, 100);
        thresholds.blindsReleasePosition = threshold(payload, "blindsReleasePosition", thresholds.blindsReleasePosition, 0, 100);

        touch();
        addEvent("info", "api", "Pragovi azurirani", "Pravila automatizacije su promijenjena.");
        List<Decision> decisions = runAutomation(false);
        notifyListeners();
        return new TelemetryResult(state, decisions);
    }

    public synchronized CommandResult applyCommand(CommandRequest request) {
        CommandResult result = applyCommand(request, true, true);
        notifyListeners();
        return result;
    }

    public synchronized List<WindowSenseState.Command> pollCommands(String requestedDeviceId) {
        String id = requestedDeviceId == null || requestedDeviceId.isBlank() ? deviceId : requestedDeviceId;
        return state.commandQueue.stream()
                .filter(command -> id.equals(command.deviceId))
                .filter(command -> "pending".equals(command.status))
                .toList();
    }

    public synchronized WindowSenseState.Command acknowledgeCommand(String commandId, String status) {
        for (WindowSenseState.Command command : state.commandQueue) {
            if (command.id.equals(commandId)) {
                command.status = status == null || status.isBlank() ? "acknowledged" : status;
                command.acknowledgedAt = WindowSenseState.now();
                addEvent("success", "device", "Komanda potvrdjena", command.target + "/" + command.action + " -> " + command.status);
                touch();
                notifyListeners();
                return command;
            }
        }

        return null;
    }

    public synchronized void setThingsBoardStatus(String connection, String lastSyncAt, String lastError) {
        state.iot.connection = connection;
        state.iot.lastSyncAt = lastSyncAt;
        state.iot.lastError = lastError;
        touch();
        notifyListeners();
    }

    private CommandResult applyCommand(CommandRequest request, boolean queue, boolean addCommandEvent) {
        String target = required(request.target(), "Nepoznat cilj komande.");
        String action = required(request.action(), "Nepoznata akcija komande.");
        String source = request.source() == null || request.source().isBlank() ? "api" : request.source();

        if (!TARGETS.contains(target)) {
            throw new IllegalArgumentException("Nepoznat cilj komande.");
        }

        if (!ACTIONS.contains(action)) {
            throw new IllegalArgumentException("Nepoznata akcija komande.");
        }

        if ("automation".equals(target)) {
            if (!"auto".equals(action) && !"manual".equals(action)) {
                throw new IllegalArgumentException("Automatizacija podrzava samo auto ili manual mod.");
            }

            state.automation.mode = action;
            touch();
            addEvent("info", source, "Automatizacija: " + action, "Nacin rada promijenjen je u " + action + ".");
            return CommandResult.mode(state.automation.mode);
        }

        if ("stop".equals(action)) {
            actuator(target).status = "idle";
            touch();
            addEvent("warning", source, "Zaustavljen " + target, "Aktuator je zaustavljen rucnom komandom.");
            return CommandResult.command(target, action, null, null);
        }

        Double position = setActuatorState(target, action, request.positionPercent());
        WindowSenseState.Command queued = queue
                ? queueCommand(new WindowSenseState.Command(deviceId, target, action, position, source))
                : null;

        if (addCommandEvent) {
            String label = "window".equals(target) ? "Prozor" : "Rolete";
            addEvent("automation".equals(source) ? "success" : "info", source, label + ": " + action,
                    "Ciljana pozicija je " + Math.round(position) + "%.");
        }

        touch();
        return CommandResult.command(target, action, position, queued);
    }

    private List<Decision> runAutomation(boolean notifyPerDecision) {
        List<Decision> decisions = automationService.evaluate(state);

        for (Decision decision : decisions) {
            applyCommand(
                    new CommandRequest(decision.target(), decision.action(), decision.positionPercent(), "automation"),
                    true,
                    true
            );
            state.automation.lastDecisionAt = WindowSenseState.now();
            addEvent("success", "automation", "Automatska odluka", decision.reason());
            if (notifyPerDecision) {
                notifyListeners();
            }
        }

        return decisions;
    }

    private WindowSenseState.Command queueCommand(WindowSenseState.Command command) {
        state.commandQueue.add(0, command);
        if (state.commandQueue.size() > 40) {
            state.commandQueue = new ArrayList<>(state.commandQueue.subList(0, 40));
        }
        return command;
    }

    private Double setActuatorState(String target, String action, Double positionPercent) {
        String now = WindowSenseState.now();
        if ("window".equals(target)) {
            double current = state.actuators.window.openPercent;
            double nextPosition = switch (action) {
                case "open" -> 100;
                case "close" -> 0;
                default -> clamp(positionPercent == null ? current : positionPercent, 0, 100);
            };
            state.actuators.window.openPercent = nextPosition;
            state.actuators.window.status = "idle";
            state.actuators.window.lastCommandAt = now;
            state.sensors.windowContactOpen = nextPosition > 0;
            return nextPosition;
        }

        double current = state.actuators.blinds.positionPercent;
        double nextPosition = switch (action) {
            case "open" -> 0;
            case "close" -> 100;
            default -> clamp(positionPercent == null ? current : positionPercent, 0, 100);
        };
        state.actuators.blinds.positionPercent = nextPosition;
        state.actuators.blinds.status = "idle";
        state.actuators.blinds.lastCommandAt = now;
        return nextPosition;
    }

    private WindowSenseState.DeviceActuator actuator(String target) {
        return "window".equals(target) ? state.actuators.window : state.actuators.blinds;
    }

    private void touchWeather() {
        String now = WindowSenseState.now();
        state.weather.updatedAt = now;
        state.updatedAt = now;
    }

    private void touch() {
        state.updatedAt = WindowSenseState.now();
    }

    private void addEvent(String level, String source, String title, String details) {
        state.events.add(0, new WindowSenseState.Event(level, source, title, details));
        if (state.events.size() > 80) {
            state.events = new ArrayList<>(state.events.subList(0, 80));
        }
    }

    private void notifyListeners() {
        for (Consumer<WindowSenseState> listener : listeners) {
            listener.accept(state);
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    private static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static boolean booleanValue(Map<String, Object> payload, String key, boolean fallback) {
        Object value = payload.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value instanceof Number number) {
            return number.intValue() == 1;
        }

        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                return true;
            }

            if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                return false;
            }
        }

        return fallback;
    }

    private static double threshold(Map<String, Object> payload, String key, double fallback, double min, double max) {
        if (!payload.containsKey(key)) {
            return fallback;
        }

        return numberValue(payload, key, fallback, min, max);
    }

    private static double numberValue(Map<String, Object> payload, String key, double fallback, double min, double max) {
        Object value = payload.get(key);
        if (value == null || "".equals(value)) {
            return fallback;
        }

        double parsed;
        if (value instanceof Number number) {
            parsed = number.doubleValue();
        } else {
            try {
                parsed = Double.parseDouble(value.toString());
            } catch (NumberFormatException error) {
                parsed = fallback;
            }
        }

        return clamp(parsed, min, max);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }

        return Math.min(max, Math.max(min, value));
    }
}
