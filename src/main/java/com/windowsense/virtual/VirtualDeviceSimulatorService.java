package com.windowsense.virtual;

import com.windowsense.device.DeviceStatus;
import com.windowsense.device.DeviceType;
import com.windowsense.device.WindowDevice;
import com.windowsense.device.WindowDeviceRepository;
import com.windowsense.room.Room;
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
    private final AtomicInteger sampleCursor = new AtomicInteger(0);

    public VirtualDeviceSimulatorService(
            WindowDeviceRepository windowDeviceRepository,
            VirtualWeatherDataService virtualWeatherDataService,
            TelemetryPublisher telemetryPublisher
    ) {
        this.windowDeviceRepository = windowDeviceRepository;
        this.virtualWeatherDataService = virtualWeatherDataService;
        this.telemetryPublisher = telemetryPublisher;
    }

    @Scheduled(fixedDelayString = "${windowsense.virtual-simulator.interval-ms:5000}")
    @Transactional(readOnly = true)
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
            telemetryPublisher.publishTelemetry(device, payload(device, randomSample(samples, baseIndex)));
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

    private Map<String, Object> payload(WindowDevice device, VirtualWeatherSample sample) {
        Room room = device.getRoom();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rainDetected", sample.rainDetected());
        payload.put("rainIntensity", sample.rainIntensity());
        payload.put("rainRiskPercent", sample.rainRiskPercent());
        payload.put("lux", sample.lux());
        payload.put("indoorTempC", sample.indoorTempC());
        payload.put("windKmh", sample.windKmh());
        payload.put("windowOpenPercent", sample.windowOpenPercent());
        payload.put("blindClosedPercent", sample.blindClosedPercent());
        payload.put("roomId", room.getId().toString());
        payload.put("roomName", room.getName());
        payload.put("deviceId", device.getId().toString());
        payload.put("isVirtual", true);
        return payload;
    }
}
