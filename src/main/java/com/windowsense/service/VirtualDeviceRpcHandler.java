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

        Command command = command(method, params);
        if (!device.hasCapability(command.capability())) {
            throw new ConflictException("DEVICE_DOES_NOT_SUPPORT_CAPABILITY");
        }

        apply(device, command);
        logRpc(device, command);
        return new VirtualDeviceRpcResult(response(device, command), telemetry(device));
    }

    private Command command(String method, Map<String, Object> params) {
        return switch (method == null ? "" : method.trim()) {
            case "openWindow" -> new Command("window", "open", DeviceCapability.WINDOW_CONTROL, 100.0);
            case "closeWindow" -> new Command("window", "close", DeviceCapability.WINDOW_CONTROL, 0.0);
            case "setWindowPosition" -> new Command("window", "setPosition", DeviceCapability.WINDOW_CONTROL, position(params));
            case "stopWindow" -> new Command("window", "stop", DeviceCapability.WINDOW_CONTROL, null);
            case "openBlinds" -> new Command("blinds", "open", DeviceCapability.BLINDS_CONTROL, 0.0);
            case "closeBlinds" -> new Command("blinds", "close", DeviceCapability.BLINDS_CONTROL, 100.0);
            case "setBlindsPosition" -> new Command("blinds", "setPosition", DeviceCapability.BLINDS_CONTROL, position(params));
            case "stopBlinds" -> new Command("blinds", "stop", DeviceCapability.BLINDS_CONTROL, null);
            default -> throw new IllegalArgumentException("Nepoznata RPC metoda: " + method);
        };
    }

    private Double position(Map<String, Object> params) {
        Object raw = params == null ? null : params.get("position");
        if (raw == null) {
            throw new IllegalArgumentException("RPC parametar position je obavezan.");
        }
        double parsed = raw instanceof Number number ? number.doubleValue() : Double.parseDouble(raw.toString());
        return Math.min(100, Math.max(0, parsed));
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

    private void logRpc(WindowDevice device, Command command) {
        runtimeStateRepository.withState(state -> {
            eventLogService.addEvent(
                    state,
                    "success",
                    "thingsboard-rpc",
                    "ThingsBoard RPC: " + command.target() + "/" + command.action(),
                    device.getRoom().getName() + " / " + device.getName() + ": komanda izvrsena."
            );
            state.updatedAt = RuntimeState.now();
            return null;
        });
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
        copyIfPresent(fullTelemetry, telemetry, "lux");
        copyIfPresent(fullTelemetry, telemetry, "indoorTempC");
        copyIfPresent(fullTelemetry, telemetry, "windKmh");
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

    private record Command(String target, String action, DeviceCapability capability, Double position) {
    }
}
