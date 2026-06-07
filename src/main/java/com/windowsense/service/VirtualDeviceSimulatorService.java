package com.windowsense.service;

import com.windowsense.entity.DeviceStatus;
import com.windowsense.entity.DeviceType;
import com.windowsense.entity.DeviceCapability;
import com.windowsense.entity.SimulationMode;
import com.windowsense.entity.WindowDevice;
import com.windowsense.repository.WindowDeviceRepository;
import com.windowsense.dto.Decision;
import com.windowsense.entity.Room;
import com.windowsense.service.RoomAutomationEvaluation;
import com.windowsense.service.RoomAutomationEvaluator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@ConditionalOnProperty(prefix = "windowsense.virtual-simulator", name = "enabled", havingValue = "true")
public class VirtualDeviceSimulatorService {

    private final WindowDeviceRepository windowDeviceRepository;
    private final VirtualWeatherDataService virtualWeatherDataService;
    private final TelemetryPublisher telemetryPublisher;
    private final RoomAutomationEvaluator roomAutomationEvaluator;
    private final AtomicInteger sampleCursor = new AtomicInteger(0);

    public VirtualDeviceSimulatorService(
            WindowDeviceRepository windowDeviceRepository,
            VirtualWeatherDataService virtualWeatherDataService,
            TelemetryPublisher telemetryPublisher,
            RoomAutomationEvaluator roomAutomationEvaluator
    ) {
        this.windowDeviceRepository = windowDeviceRepository;
        this.virtualWeatherDataService = virtualWeatherDataService;
        this.telemetryPublisher = telemetryPublisher;
        this.roomAutomationEvaluator = roomAutomationEvaluator;
    }

    @Scheduled(fixedDelayString = "${windowsense.virtual-simulator.interval-ms:5000}")
    @Transactional
    public void publishVirtualTelemetry() {
        List<VirtualWeatherSample> samples = virtualWeatherDataService.samples();
        if (samples.isEmpty()) {
            return;
        }
        int baseIndex = sampleCursor.getAndUpdate(index -> (index + 1) % samples.size());
        for (WindowDevice device : windowDeviceRepository.findActiveVirtualDevicesWithRoom()) {
            if (!isActiveVirtualDevice(device)) {
                continue;
            }
            if (device.getSimulationMode() != SimulationMode.AUTO) {
                continue;
            }
            applySample(device, randomSample(samples, baseIndex));
            RoomAutomationEvaluation evaluation = roomAutomationEvaluator.evaluateAndApply(
                    device.getRoom(),
                    device,
                    roomAutomationEvaluator.thresholds(device.getRoom()),
                    roomAutomationEvaluator.virtualTelemetry(device)
            );
            telemetryPublisher.publishTelemetry(device, payload(device, evaluation.decisions()));
        }
    }

    private boolean isActiveVirtualDevice(WindowDevice device) {
        return device.getDeviceType() == DeviceType.VIRTUAL
                && device.getStatus() == DeviceStatus.ACTIVE
                && device.isVirtual();
    }

    private VirtualWeatherSample randomSample(List<VirtualWeatherSample> samples, int baseIndex) {
        int randomOffset = ThreadLocalRandom.current().nextInt(samples.size());
        return samples.get((baseIndex + randomOffset) % samples.size());
    }

    private void applySample(WindowDevice device, VirtualWeatherSample sample) {
        device.updateSimulationTelemetry(
                sample.rainDetected(),
                sample.rainIntensity(),
                sample.rainRiskPercent(),
                sample.lux(),
                sample.indoorTempC(),
                sample.windKmh(),
                device.getSimWindowOpenPercent(),
                device.getSimBlindClosedPercent()
        );
    }

    private Map<String, Object> payload(WindowDevice device, List<Decision> decisions) {
        Room room = device.getRoom();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rainDetected", device.isSimRainDetected());
        payload.put("rainIntensity", device.getSimRainIntensity());
        payload.put("rainRiskPercent", device.getSimRainRiskPercent());
        payload.put("lux", device.getSimLux());
        payload.put("indoorTempC", device.getSimIndoorTempC());
        payload.put("windKmh", device.getSimWindKmh());
        if (device.hasCapability(DeviceCapability.WINDOW_CONTROL)) {
            payload.put("windowOpenPercent", device.getSimWindowOpenPercent());
        }
        if (device.hasCapability(DeviceCapability.BLINDS_CONTROL)) {
            payload.put("blindClosedPercent", device.getSimBlindClosedPercent());
        }
        payload.put("lastUpdatedAt", device.getSimLastUpdatedAt().toString());
        payload.put("roomId", room.getId().toString());
        payload.put("roomName", room.getName());
        payload.put("deviceId", device.getId().toString());
        payload.put("isVirtual", true);
        payload.put("automationDecisionApplied", !decisions.isEmpty());
        payload.put("automationDecisionCount", decisions.size());
        payload.put("automationTarget", "");
        payload.put("automationAction", "");
        payload.put("automationPositionPercent", -1);
        payload.put("automationReason", "");
        if (!decisions.isEmpty()) {
            Decision lastDecision = decisions.getLast();
            payload.put("automationTarget", lastDecision.target());
            payload.put("automationAction", lastDecision.action());
            payload.put("automationPositionPercent", lastDecision.positionPercent() == null ? -1 : lastDecision.positionPercent());
            payload.put("automationReason", lastDecision.reason());
        }
        return payload;
    }
}
