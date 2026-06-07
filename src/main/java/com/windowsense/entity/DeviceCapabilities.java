package com.windowsense.entity;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DeviceCapabilities {

    private static final Set<DeviceCapability> COMBINED_DEVICE = Set.of(
            DeviceCapability.WINDOW_CONTROL,
            DeviceCapability.BLINDS_CONTROL,
            DeviceCapability.ENVIRONMENT_SENSOR,
            DeviceCapability.RAIN_SENSOR,
            DeviceCapability.LIGHT_SENSOR
    );

    private DeviceCapabilities() {
    }

    public static Set<DeviceCapability> combinedDevice() {
        return EnumSet.copyOf(COMBINED_DEVICE);
    }

    public static Set<DeviceCapability> fromLabels(Collection<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return combinedDevice();
        }

        EnumSet<DeviceCapability> capabilities = EnumSet.noneOf(DeviceCapability.class);
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            capabilities.add(fromLabel(label));
        }

        return capabilities.isEmpty() ? combinedDevice() : capabilities;
    }

    public static Set<DeviceCapability> fromCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return combinedDevice();
        }
        return fromLabels(List.of(csv.split(",")));
    }

    public static DeviceCapability requiredForCommand(String target) {
        String normalized = normalize(target);
        return switch (normalized) {
            case "window" -> DeviceCapability.WINDOW_CONTROL;
            case "blinds" -> DeviceCapability.BLINDS_CONTROL;
            default -> throw new IllegalArgumentException("NO_DEVICE_FOR_CAPABILITY");
        };
    }

    private static DeviceCapability fromLabel(String label) {
        return switch (normalize(label)) {
            case "window", "window_control" -> DeviceCapability.WINDOW_CONTROL;
            case "blinds", "blinds_control" -> DeviceCapability.BLINDS_CONTROL;
            case "rain", "rain_sensor" -> DeviceCapability.RAIN_SENSOR;
            case "lux", "light", "light_sensor" -> DeviceCapability.LIGHT_SENSOR;
            case "temperature", "wind", "sensor", "environment", "environment_sensor" -> DeviceCapability.ENVIRONMENT_SENSOR;
            default -> throw new IllegalArgumentException("Nepoznat capability: " + label.trim());
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
