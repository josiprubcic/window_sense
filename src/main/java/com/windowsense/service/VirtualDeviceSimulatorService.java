package com.windowsense.service;

import com.windowsense.entity.DeviceStatus;
import com.windowsense.entity.DeviceType;
import com.windowsense.entity.DeviceCapability;
import com.windowsense.entity.SimulationMode;
import com.windowsense.entity.WindowDevice;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.exception.ThingsBoardProvisioningException;
import com.windowsense.integration.thingsboard.ThingsBoardProvisioningService;
import com.windowsense.integration.thingsboard.ThingsBoardTelemetryQueryService;
import com.windowsense.repository.WindowDeviceRepository;
import com.windowsense.dto.Decision;
import com.windowsense.entity.Room;
import com.windowsense.service.RoomAutomationEvaluation;
import com.windowsense.service.RoomAutomationEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@ConditionalOnProperty(prefix = "windowsense.virtual-simulator", name = "enabled", havingValue = "true")
public class VirtualDeviceSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(VirtualDeviceSimulatorService.class);

    private final WindowDeviceRepository windowDeviceRepository;
    private final VirtualWeatherDataService virtualWeatherDataService;
    private final TelemetryPublisher telemetryPublisher;
    private final RoomAutomationEvaluator roomAutomationEvaluator;
    private final ThingsBoardProvisioningService thingsBoardProvisioningService;
    private final ThingsBoardTelemetryQueryService thingsBoardTelemetryQueryService;
    private final WindowSenseProperties.VirtualSimulator properties;
    private final WindowSenseProperties.Automation automationProperties;
    private final AtomicInteger sampleCursor = new AtomicInteger(0);
    private final Map<UUID, String> syncedRuntimeStateByRoom = new ConcurrentHashMap<>();

    public VirtualDeviceSimulatorService(
            WindowDeviceRepository windowDeviceRepository,
            VirtualWeatherDataService virtualWeatherDataService,
            TelemetryPublisher telemetryPublisher,
            RoomAutomationEvaluator roomAutomationEvaluator,
            ThingsBoardProvisioningService thingsBoardProvisioningService,
            ThingsBoardTelemetryQueryService thingsBoardTelemetryQueryService,
            WindowSenseProperties properties
    ) {
        this.windowDeviceRepository = windowDeviceRepository;
        this.virtualWeatherDataService = virtualWeatherDataService;
        this.telemetryPublisher = telemetryPublisher;
        this.roomAutomationEvaluator = roomAutomationEvaluator;
        this.thingsBoardProvisioningService = thingsBoardProvisioningService;
        this.thingsBoardTelemetryQueryService = thingsBoardTelemetryQueryService;
        this.properties = properties.getVirtualSimulator();
        this.automationProperties = properties.getAutomation();
    }

    @Scheduled(fixedDelayString = "${windowsense.virtual-simulator.interval-ms:5000}")
    @Transactional
    public void publishVirtualTelemetry() {
        List<VirtualWeatherSample> samples = virtualWeatherDataService.samples();
        if (samples.isEmpty()) {
            return;
        }
        int baseIndex = sampleCursor.getAndUpdate(index -> (index + 1) % samples.size());
        Map<UUID, VirtualWeatherSample> samplesByHome = new LinkedHashMap<>();
        List<WindowDevice> activeDevices = windowDeviceRepository.findActiveVirtualDevicesWithRoom();
        for (WindowDevice device : activeDevices) {
            if (!isActiveVirtualDevice(device)) {
                continue;
            }
            if (device.getSimulationMode() != SimulationMode.AUTO) {
                log.debug("Virtualni simulator preskace uredjaj {} jer je u {} modu.", device.getId(), device.getSimulationMode());
                continue;
            }
            applySample(device, sampleForHome(samples, baseIndex, samplesByHome, device));
            List<Decision> decisions = List.of();
            if (!isThingsBoardAutomationActive()) {
                RoomAutomationEvaluation evaluation = roomAutomationEvaluator.evaluateAndApply(
                        device.getRoom(),
                        device,
                        roomAutomationEvaluator.thresholds(device.getRoom()),
                        roomAutomationEvaluator.virtualTelemetry(device)
                );
                decisions = evaluation.decisions();
            }
            Map<String, Object> payload = payload(device, decisions);
            syncRuleChainRuntimeSharedAttributesIfRiskChanged(device, payload, activeDevices);
            boolean published = telemetryPublisher.publishTelemetry(device, payload);
            if (!published) {
                log.warn("Virtualni simulator nije poslao ThingsBoard telemetriju za uredjaj {}.", device.getId());
            }
            if (isThingsBoardAutomationActive() && !published && shouldUseBackendFallback()) {
                RoomAutomationEvaluation fallbackEvaluation = roomAutomationEvaluator.evaluateAndApply(
                        device.getRoom(),
                        device,
                        roomAutomationEvaluator.thresholds(device.getRoom()),
                        roomAutomationEvaluator.virtualTelemetry(device)
                );
                Map<String, Object> fallbackPayload = payload(device, fallbackEvaluation.decisions());
                syncRuleChainRuntimeSharedAttributesIfRiskChanged(device, fallbackPayload, activeDevices);
                telemetryPublisher.publishTelemetry(device, fallbackPayload);
            }
        }
        publishPhysicalWeatherTelemetry(samples, baseIndex, samplesByHome);
    }

    private void publishPhysicalWeatherTelemetry(
            List<VirtualWeatherSample> samples,
            int baseIndex,
            Map<UUID, VirtualWeatherSample> samplesByHome
    ) {
        if (!properties.isPublishToThingsBoard() || !properties.isPublishPhysicalWeatherToThingsBoard()) {
            return;
        }

        for (WindowDevice device : windowDeviceRepository.findActivePhysicalDevicesWithRoomAndToken()) {
            if (!isActivePhysicalDevice(device)) {
                continue;
            }
            applySample(device, sampleForHome(samples, baseIndex, samplesByHome, device));
            Map<String, Object> payload = physicalWeatherPayload(device);
            syncRuleChainRuntimeSharedAttributes(device, payload);
            boolean published = telemetryPublisher.publishTelemetry(device, payload);
            if (!isThingsBoardAutomationActive() || (!published && shouldUseBackendFallback())) {
                evaluatePhysicalAutomation(device);
            }
        }
    }

    private boolean isThingsBoardAutomationActive() {
        return automationProperties.isThingsBoardRuleChainEngine() && properties.isPublishToThingsBoard();
    }

    private boolean shouldUseBackendFallback() {
        return automationProperties.isBackendFallbackEnabled();
    }

    private void syncRuleChainRuntimeSharedAttributesIfRiskChanged(
            WindowDevice device,
            Map<String, Object> payload,
            List<WindowDevice> activeDevices
    ) {
        if (!isThingsBoardAutomationActive() || device.getTbDeviceId() == null || device.getTbDeviceId().isBlank()) {
            return;
        }
        Map<String, Object> attributes = ruleChainRuntimeSharedAttributes(payload);
        if (attributes.isEmpty()) {
            return;
        }
        Object rainProbability = attributes.get("rainProbability");
        double rain = rainProbability instanceof Number number ? number.doubleValue() : Double.parseDouble(rainProbability.toString());
        boolean rainRisk = rain > device.getRoom().getThresholdRainProbabilityClose();
        Object day = attributes.get("day");
        String stateKey = rainRisk + ":" + (day == null ? "" : day.toString());
        UUID roomId = device.getRoom().getId();
        String previous = syncedRuntimeStateByRoom.put(roomId, stateKey);
        if (stateKey.equals(previous)) {
            return;
        }
        for (WindowDevice roomDevice : activeDevices) {
            if (roomId.equals(roomDevice.getRoom().getId())
                    && roomDevice.getTbDeviceId() != null
                    && !roomDevice.getTbDeviceId().isBlank()) {
                thingsBoardProvisioningService.syncDeviceSharedAttributes(
                        roomDevice.getTbDeviceId(),
                        attributes
                );
            }
        }
    }

    private void syncRuleChainRuntimeSharedAttributes(WindowDevice device, Map<String, Object> payload) {
        if (!isThingsBoardAutomationActive() || device.getTbDeviceId() == null || device.getTbDeviceId().isBlank()) {
            return;
        }
        Map<String, Object> attributes = ruleChainRuntimeSharedAttributes(payload);
        if (attributes.isEmpty()) {
            return;
        }
        thingsBoardProvisioningService.syncDeviceSharedAttributes(
                device.getTbDeviceId(),
                attributes
        );
    }

    private Map<String, Object> ruleChainRuntimeSharedAttributes(Map<String, Object> payload) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        Object rainProbability = payload.get("rainProbability");
        if (rainProbability == null) {
            rainProbability = payload.get("rainRiskPercent");
        }
        if (rainProbability != null) {
            attributes.put("rainProbability", rainProbability);
        }
        Object day = payload.get("day");
        if (day != null) {
            attributes.put("day", day);
        }
        return attributes;
    }

    private boolean isActiveVirtualDevice(WindowDevice device) {
        return device.getDeviceType() == DeviceType.VIRTUAL
                && device.getStatus() == DeviceStatus.ACTIVE
                && device.isVirtual();
    }

    private boolean isActivePhysicalDevice(WindowDevice device) {
        return device.getDeviceType() == DeviceType.PHYSICAL
                && device.getStatus() == DeviceStatus.ACTIVE
                && !device.isVirtual();
    }

    private VirtualWeatherSample randomSample(List<VirtualWeatherSample> samples, int baseIndex) {
        return samples.get(baseIndex % samples.size());
    }

    private VirtualWeatherSample sampleForHome(
            List<VirtualWeatherSample> samples,
            int baseIndex,
            Map<UUID, VirtualWeatherSample> samplesByHome,
            WindowDevice device
    ) {
        return samplesByHome.computeIfAbsent(homeKey(device), ignored -> randomSample(samples, baseIndex));
    }

    private UUID homeKey(WindowDevice device) {
        UUID homeId = device.getRoom().getHome().getId();
        return homeId == null ? device.getRoom().getId() : homeId;
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
                device.getSimBlindClosedPercent(),
                sample.day()
        );
    }

    private Map<String, Object> payload(WindowDevice device, List<Decision> decisions) {
        Room room = device.getRoom();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rainDetected", device.isSimRainDetected());
        payload.put("rainIntensity", device.getSimRainIntensity());
        payload.put("rainRiskPercent", device.getSimRainRiskPercent());
        payload.put("rainProbability", device.getSimRainRiskPercent());
        payload.put("windKmh", device.getSimWindKmh());
        payload.put("day", device.getSimDay());
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
        return payload;
    }

    private Map<String, Object> physicalWeatherPayload(WindowDevice device) {
        Room room = device.getRoom();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rainDetected", device.isSimRainDetected());
        payload.put("rainIntensity", device.getSimRainIntensity());
        payload.put("rainRiskPercent", device.getSimRainRiskPercent());
        payload.put("rainProbability", device.getSimRainRiskPercent());
        payload.put("windKmh", device.getSimWindKmh());
        payload.put("day", device.getSimDay());
        payload.put("lastUpdatedAt", device.getSimLastUpdatedAt().toString());
        payload.put("roomId", room.getId().toString());
        payload.put("roomName", room.getName());
        payload.put("deviceId", device.getId().toString());
        payload.put("isVirtual", false);
        payload.put("simulationSource", "windowsense-backend");
        return payload;
    }

    private void evaluatePhysicalAutomation(WindowDevice device) {
        try {
            ThingsBoardTelemetryQueryService.LatestTelemetry latest =
                    thingsBoardTelemetryQueryService.latestDeviceTelemetry(device.getTbDeviceId());
            if (latest.telemetry().isEmpty()) {
                return;
            }
            roomAutomationEvaluator.evaluateAndApply(
                    device.getRoom(),
                    device,
                    roomAutomationEvaluator.thresholds(device.getRoom()),
                    latest.telemetry()
            );
        } catch (ThingsBoardProvisioningException ignored) {
            // Physical automation will resume on the next scheduled tick when ThingsBoard is reachable.
        }
    }
}
