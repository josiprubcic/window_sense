package com.windowsense.service;

import com.windowsense.config.WindowSenseProperties;
import com.windowsense.model.CommandRequest;
import com.windowsense.model.CommandResult;
import com.windowsense.model.Decision;
import com.windowsense.model.WindowSenseState;
import com.windowsense.repository.WindowSenseStateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class CommandService {

    private static final Set<String> TARGETS = Set.of("window", "blinds", "automation");
    private static final Set<String> ACTIONS = Set.of("open", "close", "stop", "setPosition", "auto", "manual");

    private final String deviceId;
    private final WindowSenseStateRepository repository;
    private final EventLogService eventLogService;
    private final StatePublisher statePublisher;

    public CommandService(
            WindowSenseProperties properties,
            WindowSenseStateRepository repository,
            EventLogService eventLogService,
            StatePublisher statePublisher
    ) {
        this.deviceId = properties.getDeviceId();
        this.repository = repository;
        this.eventLogService = eventLogService;
        this.statePublisher = statePublisher;
    }

    public CommandResult applyCommand(CommandRequest request) {
        CommandResult result = repository.withState(state -> applyCommand(state, request, true, true));
        statePublisher.publish(repository.getState(), "command");
        return result;
    }

    public List<WindowSenseState.Command> pollCommands(String requestedDeviceId) {
        return repository.withState(state -> {
            String id = requestedDeviceId == null || requestedDeviceId.isBlank() ? deviceId : requestedDeviceId;
            return state.commandQueue.stream()
                    .filter(command -> id.equals(command.deviceId))
                    .filter(command -> "pending".equals(command.status))
                    .toList();
        });
    }

    public WindowSenseState.Command acknowledgeCommand(String commandId, String status) {
        WindowSenseState.Command command = repository.withState(state -> {
            for (WindowSenseState.Command queued : state.commandQueue) {
                if (queued.id.equals(commandId)) {
                    queued.status = status == null || status.isBlank() ? "acknowledged" : status;
                    queued.acknowledgedAt = WindowSenseState.now();
                    eventLogService.addEvent(
                            state,
                            "success",
                            "device",
                            "Komanda potvrdjena",
                            queued.target + "/" + queued.action + " -> " + queued.status
                    );
                    touch(state);
                    return queued;
                }
            }

            return null;
        });

        if (command != null) {
            statePublisher.publish(repository.getState(), "device-ack");
        }

        return command;
    }

    public void applyAutomationDecisions(WindowSenseState state, List<Decision> decisions) {
        for (Decision decision : decisions) {
            applyCommand(
                    state,
                    new CommandRequest(decision.target(), decision.action(), decision.positionPercent(), "automation"),
                    true,
                    true
            );
            state.automation.lastDecisionAt = WindowSenseState.now();
            eventLogService.addEvent(state, "success", "automation", "Automatska odluka", decision.reason());
        }
    }

    private CommandResult applyCommand(
            WindowSenseState state,
            CommandRequest request,
            boolean queue,
            boolean addCommandEvent
    ) {
        String target = PayloadValues.required(request.target(), "Nepoznat cilj komande.");
        String action = PayloadValues.required(request.action(), "Nepoznata akcija komande.");
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
            touch(state);
            eventLogService.addEvent(state, "info", source, "Automatizacija: " + action,
                    "Nacin rada promijenjen je u " + action + ".");
            return CommandResult.mode(state.automation.mode);
        }

        if ("stop".equals(action)) {
            actuator(state, target).status = "idle";
            touch(state);
            eventLogService.addEvent(state, "warning", source, "Zaustavljen " + target,
                    "Aktuator je zaustavljen rucnom komandom.");
            return CommandResult.command(target, action, null, null);
        }

        Double position = setActuatorState(state, target, action, request.positionPercent());
        WindowSenseState.Command queued = queue
                ? queueCommand(state, new WindowSenseState.Command(deviceId, target, action, position, source))
                : null;

        if (addCommandEvent) {
            String label = "window".equals(target) ? "Prozor" : "Rolete";
            eventLogService.addEvent(state, "automation".equals(source) ? "success" : "info", source,
                    label + ": " + action, "Ciljana pozicija je " + Math.round(position) + "%.");
        }

        touch(state);
        return CommandResult.command(target, action, position, queued);
    }

    private WindowSenseState.Command queueCommand(WindowSenseState state, WindowSenseState.Command command) {
        state.commandQueue.add(0, command);
        if (state.commandQueue.size() > 40) {
            state.commandQueue = new ArrayList<>(state.commandQueue.subList(0, 40));
        }
        return command;
    }

    private Double setActuatorState(WindowSenseState state, String target, String action, Double positionPercent) {
        String now = WindowSenseState.now();
        if ("window".equals(target)) {
            double current = state.actuators.window.openPercent;
            double nextPosition = switch (action) {
                case "open" -> 100;
                case "close" -> 0;
                default -> PayloadValues.clamp(positionPercent == null ? current : positionPercent, 0, 100);
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
            default -> PayloadValues.clamp(positionPercent == null ? current : positionPercent, 0, 100);
        };
        state.actuators.blinds.positionPercent = nextPosition;
        state.actuators.blinds.status = "idle";
        state.actuators.blinds.lastCommandAt = now;
        return nextPosition;
    }

    private WindowSenseState.DeviceActuator actuator(WindowSenseState state, String target) {
        return "window".equals(target) ? state.actuators.window : state.actuators.blinds;
    }

    private void touch(WindowSenseState state) {
        state.updatedAt = WindowSenseState.now();
    }
}
