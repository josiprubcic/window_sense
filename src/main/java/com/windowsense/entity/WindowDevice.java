package com.windowsense.entity;

import com.windowsense.entity.Room;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "window_device",
        uniqueConstraints = @UniqueConstraint(name = "uq_window_device_room_name", columnNames = {"room_id", "name"})
)
public class WindowDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 20)
    private DeviceType deviceType;

    @Column(name = "is_virtual", nullable = false)
    private boolean virtual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status;

    @Column(name = "tb_device_id", nullable = false, length = 128)
    private String tbDeviceId;

    @Column(name = "tb_device_token_encrypted", columnDefinition = "TEXT")
    private String tbDeviceTokenEncrypted;

    @Column(name = "physical_hardware_id", unique = true, length = 128)
    private String physicalHardwareId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "window_device_capabilities",
            joinColumns = @JoinColumn(name = "window_device_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "capability", nullable = false, length = 40)
    private Set<DeviceCapability> capabilities = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "simulation_mode", nullable = false, length = 20)
    private SimulationMode simulationMode = SimulationMode.AUTO;

    @Column(name = "sim_rain_detected", nullable = false)
    private boolean simRainDetected = false;

    @Column(name = "sim_rain_intensity", nullable = false)
    private double simRainIntensity = 0;

    @Column(name = "sim_rain_risk_percent", nullable = false)
    private double simRainRiskPercent = 12;

    @Column(name = "sim_lux", nullable = false)
    private double simLux = 52000;

    @Column(name = "sim_indoor_temp_c", nullable = false)
    private double simIndoorTempC = 23.5;

    @Column(name = "sim_wind_kmh", nullable = false)
    private double simWindKmh = 8;

    @Column(name = "sim_window_open_percent", nullable = false)
    private double simWindowOpenPercent = 72;

    @Column(name = "sim_blind_closed_percent", nullable = false)
    private double simBlindClosedPercent = 20;

    @Column(name = "sim_day", nullable = false)
    private int simDay = 1;

    @Column(name = "sim_last_updated_at", nullable = false)
    private Instant simLastUpdatedAt = Instant.now();

    @Column(name = "desired_angle_day", nullable = false)
    private double desiredAngleDay = 90;

    @Column(name = "desired_angle_night", nullable = false)
    private double desiredAngleNight = 0;

    @Column(name = "desired_angle_rain", nullable = false)
    private double desiredAngleRain = 15;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WindowDevice() {
    }

    private WindowDevice(String name, DeviceType deviceType, boolean virtual, DeviceStatus status, String tbDeviceId) {
        validateVirtualConsistency(deviceType, virtual);
        this.name = name;
        this.deviceType = deviceType;
        this.virtual = virtual;
        this.status = status;
        this.tbDeviceId = tbDeviceId;
    }

    public static WindowDevice virtualDevice(String name, String tbDeviceId) {
        return virtualDevice(name, tbDeviceId, DeviceCapabilities.combinedDevice());
    }

    public static WindowDevice virtualDevice(String name, String tbDeviceId, Set<DeviceCapability> capabilities) {
        WindowDevice device = new WindowDevice(name, DeviceType.VIRTUAL, true, DeviceStatus.ACTIVE, tbDeviceId);
        device.replaceCapabilities(capabilities);
        return device;
    }

    public static WindowDevice physicalDevice(String name, String tbDeviceId) {
        return physicalDevice(name, tbDeviceId, null, DeviceCapabilities.combinedDevice());
    }

    public static WindowDevice physicalDevice(
            String name,
            String tbDeviceId,
            String physicalHardwareId,
            Set<DeviceCapability> capabilities
    ) {
        WindowDevice device = new WindowDevice(name, DeviceType.PHYSICAL, false, DeviceStatus.ACTIVE, tbDeviceId);
        device.physicalHardwareId = blankToNull(physicalHardwareId);
        device.replaceCapabilities(capabilities);
        return device;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void assignRoom(Room room) {
        this.room = room;
    }

    public void updateThingsBoardDevice(String tbDeviceId) {
        this.tbDeviceId = tbDeviceId;
    }

    public void storeEncryptedThingsBoardDeviceToken(String tbDeviceTokenEncrypted) {
        this.tbDeviceTokenEncrypted = tbDeviceTokenEncrypted;
    }

    public UUID getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public String getName() {
        return name;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public boolean isVirtual() {
        return virtual;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public String getTbDeviceId() {
        return tbDeviceId;
    }

    public String getTbDeviceTokenEncrypted() {
        return tbDeviceTokenEncrypted;
    }

    public String getPhysicalHardwareId() {
        return physicalHardwareId;
    }

    public Set<DeviceCapability> getCapabilities() {
        if (capabilities == null || capabilities.isEmpty()) {
            return DeviceCapabilities.combinedDevice();
        }
        return EnumSet.copyOf(capabilities);
    }

    public boolean hasCapability(DeviceCapability capability) {
        return getCapabilities().contains(capability);
    }

    public SimulationMode getSimulationMode() {
        return simulationMode;
    }

    public boolean isSimRainDetected() {
        return simRainDetected;
    }

    public double getSimRainIntensity() {
        return simRainIntensity;
    }

    public double getSimRainRiskPercent() {
        return simRainRiskPercent;
    }

    public double getSimLux() {
        return simLux;
    }

    public double getSimIndoorTempC() {
        return simIndoorTempC;
    }

    public double getSimWindKmh() {
        return simWindKmh;
    }

    public double getSimWindowOpenPercent() {
        return simWindowOpenPercent;
    }

    public double getSimBlindClosedPercent() {
        return simBlindClosedPercent;
    }

    public int getSimDay() {
        return simDay;
    }

    public Instant getSimLastUpdatedAt() {
        return simLastUpdatedAt;
    }

    public double getDesiredAngleDay() {
        return desiredAngleDay;
    }

    public double getDesiredAngleNight() {
        return desiredAngleNight;
    }

    public double getDesiredAngleRain() {
        return desiredAngleRain;
    }

    public void updateDesiredAngles(double day, double night, double rain) {
        this.desiredAngleDay = clamp(day, 0, 90);
        this.desiredAngleNight = clamp(night, 0, 90);
        this.desiredAngleRain = clamp(rain, 0, 90);
    }

    public void updateSimulationMode(SimulationMode simulationMode) {
        this.simulationMode = simulationMode;
        this.simLastUpdatedAt = Instant.now();
    }

    public void updateSimulationTelemetry(
            boolean rainDetected,
            double rainIntensity,
            double rainRiskPercent,
            double lux,
            double indoorTempC,
            double windKmh,
            double windowOpenPercent,
            double blindClosedPercent
    ) {
        updateSimulationTelemetry(
                rainDetected,
                rainIntensity,
                rainRiskPercent,
                lux,
                indoorTempC,
                windKmh,
                windowOpenPercent,
                blindClosedPercent,
                simDay
        );
    }

    public void updateSimulationTelemetry(
            boolean rainDetected,
            double rainIntensity,
            double rainRiskPercent,
            double lux,
            double indoorTempC,
            double windKmh,
            double windowOpenPercent,
            double blindClosedPercent,
            int day
    ) {
        this.simRainDetected = rainDetected;
        this.simRainIntensity = clamp(rainIntensity, 0, 100);
        this.simRainRiskPercent = clamp(rainRiskPercent, 0, 100);
        this.simLux = clamp(lux, 0, 120000);
        this.simIndoorTempC = clamp(indoorTempC, -30, 80);
        this.simWindKmh = clamp(windKmh, 0, 250);
        this.simWindowOpenPercent = clamp(windowOpenPercent, 0, 100);
        this.simBlindClosedPercent = clamp(blindClosedPercent, 0, 100);
        this.simDay = day <= 0 ? 0 : 1;
        this.simLastUpdatedAt = Instant.now();
    }

    private static void validateVirtualConsistency(DeviceType deviceType, boolean virtual) {
        if (deviceType == DeviceType.VIRTUAL && !virtual) {
            throw new IllegalArgumentException("Virtualni uredjaj mora imati isVirtual=true.");
        }

        if (deviceType == DeviceType.PHYSICAL && virtual) {
            throw new IllegalArgumentException("Fizicki uredjaj mora imati isVirtual=false.");
        }
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }

        return Math.min(max, Math.max(min, value));
    }

    private void replaceCapabilities(Set<DeviceCapability> capabilities) {
        this.capabilities.clear();
        if (capabilities == null || capabilities.isEmpty()) {
            this.capabilities.addAll(DeviceCapabilities.combinedDevice());
        } else {
            this.capabilities.addAll(capabilities);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
