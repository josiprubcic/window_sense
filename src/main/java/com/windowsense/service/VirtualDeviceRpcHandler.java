package com.windowsense.service;

import com.windowsense.exception.ConflictException;
import com.windowsense.exception.ResourceNotFoundException;
import com.windowsense.entity.DeviceCapability;
import com.windowsense.entity.DeviceStatus;
import com.windowsense.entity.DeviceType;
import com.windowsense.entity.WindowDevice;
import com.windowsense.repository.WindowDeviceRepository;
import com.windowsense.entity.RuntimeState;
import com.windowsense.repository.RuntimeStateRepository;
import com.windowsense.entity.Room;
import com.windowsense.service.RoomAutomationEvaluator;
import com.windowsense.service.EventLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class VirtualDeviceRpcHandler {

    private final WindowDeviceRepository windowDeviceRepository;
    private final RoomAutomationEvaluator roomAutomationEvaluator;
    private final RuntimeStateRepository runtimeStateRepository;
    private final EventLogService eventLogService;

    public VirtualDeviceRpcHandler(
            WindowDeviceRepository windowDeviceRepository,
            RoomAutomationEvaluator roomAutomationEvaluator,
            RuntimeStateRepository runtimeStateRepository,
            EventLogService eventLogService
    ) {
        this.windowDeviceRepository = windowDeviceRepository;
        this.roomAutomationEvaluator = roomAutomationEvaluator;
        this.runtimeStateRepository = runtimeStateRepository;
        this.eventLogService = eventLogService;
    }

    @Transactional
    public VirtualDeviceRpcResult handle(UUID localDeviceId, String method, Map<String, Object> params) {
        WindowDevice device = windowDeviceRepository.findById(localDeviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Virtualni uredjaj nije pronadjen."));
        if (device.getDeviceType() != DeviceType.VIRTUAL || device.getStatus() != DeviceStatus.ACTIVE) {
            throw new ConflictException("VIRTUAL_DEVICE_NOT_ACTIVE");
        }

        Command command = command(device, method, params);
        if (!device.hasCapability(command.capability())) {
            throw new ConflictException("DEVICE_DOES_NOT_SUPPORT_CAPABILITY");
        }

        boolean changed = changesState(device, command);
        if (changed) {
            apply(device, command);
            logRpc(device, command, params);
        }
        return new VirtualDeviceRpcResult(response(device, command), telemetry(device), changed);
    }

    private Command command(WindowDevice device, String method, Map<String, Object> params) {
        return switch (method == null ? "" : method.trim()) {
            case "openWindow" -> new Command(method, "window", "open", DeviceCapability.WINDOW_CONTROL, 100.0);
            case "closeWindow" -> new Command(method, "window", "close", DeviceCapability.WINDOW_CONTROL, 0.0);
            case "setWindowPosition" -> new Command(method, "window", "setPosition", DeviceCapability.WINDOW_CONTROL, position(params));
            case "setAngle" -> setAngleCommand(device, method, params);
            case "stopWindow" -> new Command(method, "window", "stop", DeviceCapability.WINDOW_CONTROL, null);
            case "openBlinds" -> new Command(method, "blinds", "open", DeviceCapability.BLINDS_CONTROL, 0.0);
            case "closeBlinds" -> new Command(method, "blinds", "close", DeviceCapability.BLINDS_CONTROL, 100.0);
            case "setBlindsPosition" -> new Command(method, "blinds", "setPosition", DeviceCapability.BLINDS_CONTROL, position(params));
            case "stopBlinds" -> new Command(method, "blinds", "stop", DeviceCapability.BLINDS_CONTROL, null);
            default -> throw new IllegalArgumentException("Nepoznata RPC metoda: " + method);
        };
    }

    private Command setAngleCommand(WindowDevice device, String method, Map<String, Object> params) {
        double position = anglePosition(params);
        if (device.hasCapability(DeviceCapability.WINDOW_CONTROL)) {
            return new Command(method, "window", "setAngle", DeviceCapability.WINDOW_CONTROL, position);
        }
        if (device.hasCapability(DeviceCapability.BLINDS_CONTROL)) {
            return new Command(method, "blinds", "setAngle", DeviceCapability.BLINDS_CONTROL, position);
        }
        return new Command(method, "window", "setAngle", DeviceCapability.WINDOW_CONTROL, position);
    }

    private Double position(Map<String, Object> params) {
        Object raw = params == null ? null : params.get("position");
        if (raw == null) {
            throw new IllegalArgumentException("RPC parametar position je obavezan.");
        }
        double parsed = raw instanceof Number number ? number.doubleValue() : Double.parseDouble(raw.toString());
        return Math.min(100, Math.max(0, parsed));
    }

    private Double anglePosition(Map<String, Object> params) {
        Object raw = params == null ? null : params.get("value");
        if (raw == null) {
            raw = params == null ? null : params.get("angle");
        }
        if (raw == null) {
            raw = params == null ? null : params.get("position");
        }
        if (raw == null) {
            throw new IllegalArgumentException("RPC parametar angle je obavezan.");
        }
        double parsed = raw instanceof Number number ? number.doubleValue() : Double.parseDouble(raw.toString());
        double clampedAngle = Math.min(90, Math.max(0, parsed));
        return clampedAngle / 90.0 * 100.0;
    }

    private void apply(WindowDevice device, Command command) {
        if ("stop".equals(command.action())) {
            return;
        }

        double windowOpen = device.getSimWindowOpenPercent();
        double blindClosed = device.getSimBlindClosedPercent();
        if ("window".equals(command.target())) {
            windowOpen = command.position();
        } else if ("blinds".equals(command.target())) {
            blindClosed = command.position();
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

    private boolean changesState(WindowDevice device, Command command) {
        if ("stop".equals(command.action()) || command.position() == null) {
            return false;
        }
        double current = "window".equals(command.target())
                ? device.getSimWindowOpenPercent()
                : device.getSimBlindClosedPercent();
        return Math.abs(current - command.position()) >= 0.5;
    }

    private void logRpc(WindowDevice device, Command command, Map<String, Object> params) {
        boolean dashboardCommand = params != null && params.containsKey("commandId");
        runtimeStateRepository.withState(state -> {
            eventLogService.addRoomEvent(
                    state,
                    "success",
                    "thingsboard-rpc",
                    rpcEventTitle(command, dashboardCommand),
                    rpcDecisionDetails(device, command, dashboardCommand),
                    device.getRoom(),
                    device,
                    dashboardCommand ? "Rucna komanda iz dashboarda" : "Automatska odluka ThingsBoard rule chaina"
            );
            state.updatedAt = RuntimeState.now();
            return null;
        });
    }

    private String rpcEventTitle(Command command, boolean dashboardCommand) {
        String prefix = dashboardCommand ? "Dashboard RPC izvrsen: " : "Rule chain odluka: ";
        return prefix + command.target() + "/" + command.method();
    }

    private String rpcDecisionDetails(WindowDevice device, Command command, boolean dashboardCommand) {
        String origin = dashboardCommand
                ? "ThingsBoard je izvrsio dashboard/backend RPC komandu. "
                : "ThingsBoard rule chain je izvrsio RPC komandu. ";
        return device.getRoom().getName() + " / " + device.getName()
                + ": " + origin
                + "Akcija: " + actionDescription(command)
                + positionDescription(command)
                + ". Telemetrija: day=" + device.getSimDay()
                + ", rainDetected=" + device.isSimRainDetected()
                + ", rainProbability=" + formatNumber(device.getSimRainRiskPercent()) + "%"
                + ", rainIntensity=" + formatNumber(device.getSimRainIntensity())
                + ", windKmh=" + formatNumber(device.getSimWindKmh()) + ".";
    }

    private String actionDescription(Command command) {
        return switch (command.target() + "/" + command.action()) {
            case "window/open" -> "otvori prozor";
            case "window/close" -> "zatvori prozor";
            case "window/setAngle" -> "postavi kut prozora";
            case "window/setPosition" -> "postavi polozaj prozora";
            case "window/stop" -> "zaustavi prozor";
            case "blinds/setAngle" -> "postavi kut rolete";
            case "blinds/open" -> "podigni rolete";
            case "blinds/close" -> "spusti rolete";
            case "blinds/setPosition" -> "postavi polozaj roleta";
            case "blinds/stop" -> "zaustavi rolete";
            default -> command.target() + "/" + command.action();
        };
    }

    private String positionDescription(Command command) {
        if (command.position() == null) {
            return "";
        }
        if ("blinds".equals(command.target())) {
            return " na " + formatNumber(command.position()) + "% zatvoreno";
        }
        if ("setAngle".equals(command.action())) {
            double angle = command.position() / 100.0 * 90.0;
            return " na " + formatNumber(angle) + " deg (" + formatNumber(command.position()) + "% otvoreno)";
        }
        return " na " + formatNumber(command.position()) + "% otvoreno";
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private Map<String, Object> response(WindowDevice device, Command command) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "EXECUTED");
        response.put("target", command.target());
        response.put("action", command.action());
        response.put("localDeviceId", device.getId().toString());
        if (command.position() != null) {
            response.put("position", command.position());
        }
        return response;
    }

    private Map<String, Object> telemetry(WindowDevice device) {
        Room room = device.getRoom();
        Map<String, Object> fullTelemetry = roomAutomationEvaluator.virtualTelemetry(device);
        Map<String, Object> telemetry = new LinkedHashMap<>();
        copyIfPresent(fullTelemetry, telemetry, "rainDetected");
        copyIfPresent(fullTelemetry, telemetry, "rainIntensity");
        copyIfPresent(fullTelemetry, telemetry, "rainRiskPercent");
        copyIfPresent(fullTelemetry, telemetry, "rainProbability");
        copyIfPresent(fullTelemetry, telemetry, "windKmh");
        copyIfPresent(fullTelemetry, telemetry, "day");
        copyIfPresent(fullTelemetry, telemetry, "lastUpdatedAt");
        if (device.hasCapability(DeviceCapability.WINDOW_CONTROL)) {
            copyIfPresent(fullTelemetry, telemetry, "windowOpenPercent");
        }
        if (device.hasCapability(DeviceCapability.BLINDS_CONTROL)) {
            copyIfPresent(fullTelemetry, telemetry, "blindClosedPercent");
        }
        telemetry.put("roomId", room.getId().toString());
        telemetry.put("roomName", room.getName());
        telemetry.put("deviceId", device.getId().toString());
        telemetry.put("isVirtual", true);
        telemetry.put("simulationMode", device.getSimulationMode().name());
        telemetry.put("lastRpcApplied", true);
        return telemetry;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private record Command(String method, String target, String action, DeviceCapability capability, Double position) {
    }
}
