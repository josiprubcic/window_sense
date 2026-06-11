package com.windowsense.service;

import com.windowsense.service.AutomationInput;
import com.windowsense.service.AutomationService;
import com.windowsense.dto.AutomationThresholds;
import com.windowsense.entity.DeviceCapabilities;
import com.windowsense.entity.DeviceType;
import com.windowsense.entity.Room;
import com.windowsense.entity.WindowDevice;
import com.windowsense.dto.CommandRequest;
import com.windowsense.dto.Decision;
import com.windowsense.repository.RuntimeStateRepository;
import com.windowsense.service.CommandDeliveryPort;
import com.windowsense.service.CommandService;
import com.windowsense.service.EventLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoomAutomationEvaluator {

    private final AutomationService automationService;
    private final CommandService commandService;
    private final CommandDeliveryPort commandDeliveryPort;
    private final EventLogService eventLogService;
    private final RuntimeStateRepository runtimeStateRepository;

    @Autowired
    public RoomAutomationEvaluator(
            AutomationService automationService,
            CommandService commandService,
            CommandDeliveryPort commandDeliveryPort,
            EventLogService eventLogService,
            RuntimeStateRepository runtimeStateRepository
    ) {
        this.automationService = automationService;
        this.commandService = commandService;
        this.commandDeliveryPort = commandDeliveryPort;
        this.eventLogService = eventLogService;
        this.runtimeStateRepository = runtimeStateRepository;
    }

    public RoomAutomationEvaluator(AutomationService automationService, CommandService commandService) {
        this(automationService, commandService, null, new EventLogService(), new RuntimeStateRepository());
    }

    public RoomAutomationEvaluator(
            AutomationService automationService,
            CommandService commandService,
            EventLogService eventLogService,
            RuntimeStateRepository runtimeStateRepository
    ) {
        this(automationService, commandService, null, eventLogService, runtimeStateRepository);
    }

    public RoomAutomationEvaluation evaluateAndApply(
            Room room,
            WindowDevice device,
            AutomationThresholds thresholds,
            Map<String, Object> telemetry
    ) {
        AutomationInput input = automationInputFrom(thresholds, telemetry);
        List<Decision> decisions = automationService.evaluate(input).stream()
                .filter(decision -> device.hasCapability(DeviceCapabilities.requiredForCommand(decision.target())))
                .toList();
        if (decisions.isEmpty()) {
            return new RoomAutomationEvaluation(telemetry, decisions);
        }

        logAutomationDecisions(room, device, decisions);

        if (device.getDeviceType() == DeviceType.VIRTUAL) {
            applyVirtualDecisions(device, decisions);
            return new RoomAutomationEvaluation(virtualTelemetry(device), decisions);
        }

        for (Decision decision : decisions) {
            CommandRequest command = new CommandRequest(decision.target(), decision.action(), decision.positionPercent(), "room-automation");
            if (commandDeliveryPort == null) {
                commandService.enqueueDeviceCommand(device.getTbDeviceId(), command);
            } else {
                commandDeliveryPort.deliver(room.getId(), device, command);
            }
        }
        return new RoomAutomationEvaluation(telemetry, decisions);
    }

    private void logAutomationDecisions(Room room, WindowDevice device, List<Decision> decisions) {
        runtimeStateRepository.withState(state -> {
            for (Decision decision : decisions) {
                eventLogService.addEvent(
                        state,
                        "info",
                        "room-automation",
                        "Automatska odluka: " + targetLabel(decision.target()) + "/" + actionLabel(decision.action()),
                        room.getName() + " / " + device.getName() + ": " + decision.reason()
                );
            }
            return null;
        });
    }

    public Map<String, Object> virtualTelemetry(WindowDevice device) {
        Map<String, Object> telemetry = new LinkedHashMap<>();
        telemetry.put("rainDetected", device.isSimRainDetected());
        telemetry.put("rainIntensity", device.getSimRainIntensity());
        telemetry.put("rainRiskPercent", device.getSimRainRiskPercent());
        telemetry.put("rainProbability", device.getSimRainRiskPercent());
        telemetry.put("windKmh", device.getSimWindKmh());
        telemetry.put("windowOpenPercent", device.getSimWindowOpenPercent());
        telemetry.put("blindClosedPercent", device.getSimBlindClosedPercent());
        telemetry.put("day", device.getSimDay());
        Instant lastUpdatedAt = device.getSimLastUpdatedAt();
        if (lastUpdatedAt != null) {
            telemetry.put("lastUpdatedAt", lastUpdatedAt.toString());
        }
        return telemetry;
    }

    public AutomationThresholds thresholds(Room room) {
        AutomationThresholds thresholds = new AutomationThresholds();
        thresholds.rainIntensityClose = room.getThresholdRainIntensityClose();
        thresholds.rainProbabilityClose = room.getThresholdRainProbabilityClose();
        thresholds.windKphClose = room.getThresholdWindKphClose();
        thresholds.blindsShadePosition = room.getThresholdBlindsShadePosition();
        thresholds.blindsReleasePosition = room.getThresholdBlindsReleasePosition();
        return thresholds;
    }

    private AutomationInput automationInputFrom(
            AutomationThresholds thresholds,
            Map<String, Object> telemetry
    ) {
        AutomationInput input = new AutomationInput();
        input.mode = "auto";
        input.thresholds = thresholds;
        input.rainDetected = booleanValue(telemetry, false, "rainDetected");
        input.rainIntensity = numberValue(telemetry, 0, "rainIntensity");
        input.day = (int) numberValue(telemetry, 1, "day");
        input.rainProbability = numberValue(telemetry, 0, "rainRiskPercent", "rainProbability");
        input.windKph = numberValue(telemetry, 0, "windKmh", "windKph");
        input.windowOpenPercent = numberValue(telemetry, 0, "windowOpenPercent");
        input.blindsPositionPercent = numberValue(telemetry, 0, "blindClosedPercent", "blindsPositionPercent");
        return input;
    }

    private void applyVirtualDecisions(WindowDevice device, List<Decision> decisions) {
        double windowOpen = device.getSimWindowOpenPercent();
        double blindClosed = device.getSimBlindClosedPercent();
        for (Decision decision : decisions) {
            if ("window".equals(decision.target())) {
                windowOpen = position("window", decision.action(), decision.positionPercent(), windowOpen);
            } else if ("blinds".equals(decision.target())) {
                blindClosed = position("blinds", decision.action(), decision.positionPercent(), blindClosed);
            }
        }
        device.updateSimulationTelemetry(
                device.isSimRainDetected(),
                device.getSimRainIntensity(),
                device.getSimRainRiskPercent(),
                device.getSimLux(),
                device.getSimIndoorTempC(),
                device.getSimWindKmh(),
                windowOpen,
                blindClosed
        );
    }

    private double position(String target, String action, Double requested, double current) {
        if ("window".equals(target)) {
            return switch (action) {
                case "open" -> 100;
                case "close" -> 0;
                case "setPosition" -> clamp(requested == null ? current : requested);
                default -> current;
            };
        }
        return switch (action) {
            case "open" -> 0;
            case "close" -> 100;
            case "setPosition" -> clamp(requested == null ? current : requested);
            default -> current;
        };
    }

    private double clamp(double value) {
        return Math.min(100, Math.max(0, value));
    }

    private String targetLabel(String target) {
        return switch (target) {
            case "window" -> "prozor";
            case "blinds" -> "rolete";
            default -> target;
        };
    }

    private String actionLabel(String action) {
        return switch (action) {
            case "open" -> "otvori";
            case "close" -> "zatvori";
            case "setPosition" -> "postavi";
            default -> action;
        };
    }

    private boolean booleanValue(Map<String, Object> payload, boolean fallback, String... keys) {
        for (String key : keys) {
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
        }
        return fallback;
    }

    private double numberValue(Map<String, Object> payload, double fallback, String... keys) {
        for (String key : keys) {
            if (!payload.containsKey(key)) {
                continue;
            }
            Object value = payload.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value != null && !value.toString().isBlank()) {
                try {
                    return Double.parseDouble(value.toString());
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
        }
        return fallback;
    }
}
