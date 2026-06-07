package com.windowsense.service;

import com.windowsense.dto.CommandRequest;
import com.windowsense.service.CommandResult;
import com.windowsense.entity.RuntimeState;
import com.windowsense.repository.RuntimeStateRepository;
import com.windowsense.exception.ResourceNotFoundException;
import com.windowsense.entity.DeviceStatus;
import com.windowsense.entity.DeviceType;
import com.windowsense.entity.WindowDevice;
import com.windowsense.repository.WindowDeviceRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class CommandService {

    private static final Set<String> ACTIONS = Set.of("open", "close", "stop", "setPosition", "auto", "manual");

    private final RuntimeStateRepository repository;
    private final EventLogService eventLogService;
    private final WindowDeviceRepository windowDeviceRepository;

    public CommandService(
            RuntimeStateRepository repository,
            EventLogService eventLogService,
            WindowDeviceRepository windowDeviceRepository
    ) {
        this.repository = repository;
        this.eventLogService = eventLogService;
        this.windowDeviceRepository = windowDeviceRepository;
    }

    public CommandResult enqueueDeviceCommand(String targetDeviceId, CommandRequest request) {
        String deviceId = PayloadValues.required(targetDeviceId, "ID uredjaja je obavezan.");
        CommandResult result = repository.withState(state -> {
            CommandResult prepared = prepareDeviceCommand(deviceId, request);
            RuntimeState.Command queued = queueCommand(state, prepared.queued);
            eventLogService.addEvent(
                    state,
                    "info",
                    queued.source,
                    "Sobna komanda: " + queued.target + "/" + queued.action,
                    "Komanda je dodana u queue za uredjaj " + deviceId + "."
            );
            touch(state);
            return CommandResult.command(queued.target, queued.action, queued.positionPercent, queued);
        });
        return result;
    }

    public CommandResult prepareDeviceCommand(String targetDeviceId, CommandRequest request) {
        String deviceId = PayloadValues.required(targetDeviceId, "ID uredjaja je obavezan.");
        String target = PayloadValues.required(request.target(), "Nepoznat cilj komande.");
        String action = PayloadValues.required(request.action(), "Nepoznata akcija komande.");
        String source = request.source() == null || request.source().isBlank() ? "web" : request.source();

        if (!"window".equals(target) && !"blinds".equals(target)) {
            throw new IllegalArgumentException("Sobne komande podrzavaju samo prozor i rolete.");
        }

        if (!ACTIONS.contains(action) || "auto".equals(action) || "manual".equals(action)) {
            throw new IllegalArgumentException("Nepoznata akcija komande.");
        }

        Double position = commandPosition(target, action, request.positionPercent(), null);
        RuntimeState.Command command = new RuntimeState.Command(deviceId, target, action, position, source);
        return CommandResult.command(target, action, position, command);
    }

    private List<RuntimeState.Command> pollCommands(String requestedDeviceId) {
        String id = PayloadValues.required(requestedDeviceId, "deviceId je obavezan.");
        return repository.withState(state -> {
            return state.commandQueue.stream()
                    .filter(command -> id.equals(command.deviceId))
                    .filter(command -> "pending".equals(command.status))
                    .toList();
        });
    }

    public List<RuntimeState.Command> pollCommandsForSerialNumber(String serialNumber) {
        WindowDevice device = physicalDeviceBySerialNumber(serialNumber);
        return pollCommands(device.getTbDeviceId());
    }

    public RuntimeState.Command acknowledgeCommand(String commandId, String requestedDeviceId, String status) {
        String id = PayloadValues.required(requestedDeviceId, "deviceId je obavezan.");
        RuntimeState.Command command = repository.withState(state -> {
            for (RuntimeState.Command queued : state.commandQueue) {
                if (queued.id.equals(commandId) && id.equals(queued.deviceId)) {
                    queued.status = status == null || status.isBlank() ? "acknowledged" : status;
                    queued.acknowledgedAt = RuntimeState.now();
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

        return command;
    }

    public RuntimeState.Command acknowledgeCommandForSerialNumber(String commandId, String serialNumber, String status) {
        WindowDevice device = physicalDeviceBySerialNumber(serialNumber);
        return acknowledgeCommand(commandId, device.getTbDeviceId(), status);
    }

    private WindowDevice physicalDeviceBySerialNumber(String serialNumber) {
        String id = PayloadValues.required(serialNumber, "serialNumber je obavezan.").trim();
        WindowDevice device = windowDeviceRepository.findByPhysicalHardwareId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fizicki uredjaj nije povezan sa sobom."));
        if (device.getDeviceType() != DeviceType.PHYSICAL || device.getStatus() != DeviceStatus.ACTIVE) {
            throw new ResourceNotFoundException("Fizicki uredjaj nije aktivan.");
        }
        return device;
    }

    private RuntimeState.Command queueCommand(RuntimeState state, RuntimeState.Command command) {
        state.commandQueue.add(0, command);
        if (state.commandQueue.size() > 40) {
            state.commandQueue = new ArrayList<>(state.commandQueue.subList(0, 40));
        }
        return command;
    }

    private Double commandPosition(String target, String action, Double positionPercent, Double currentPosition) {
        if ("stop".equals(action)) {
            return null;
        }

        if ("window".equals(target)) {
            return switch (action) {
                case "open" -> 100.0;
                case "close" -> 0.0;
                case "setPosition" -> PayloadValues.clamp(
                        positionPercent == null ? requiredCurrentPosition(currentPosition) : positionPercent,
                        0,
                        100
                );
                default -> throw new IllegalArgumentException("Nepoznata akcija komande.");
            };
        }

        return switch (action) {
            case "open" -> 0.0;
            case "close" -> 100.0;
            case "setPosition" -> PayloadValues.clamp(
                    positionPercent == null ? requiredCurrentPosition(currentPosition) : positionPercent,
                    0,
                    100
            );
            default -> throw new IllegalArgumentException("Nepoznata akcija komande.");
        };
    }

    private double requiredCurrentPosition(Double currentPosition) {
        if (currentPosition == null) {
            throw new IllegalArgumentException("Pozicija je obavezna za setPosition komandu.");
        }
        return currentPosition;
    }

    private void touch(RuntimeState state) {
        state.updatedAt = RuntimeState.now();
    }
}
