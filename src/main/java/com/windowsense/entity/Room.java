package com.windowsense.entity;

import com.windowsense.entity.WindowDevice;
import com.windowsense.entity.Home;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "room",
        uniqueConstraints = @UniqueConstraint(name = "uq_room_home_name", columnNames = {"home_id", "name"})
)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_id", nullable = false)
    private Home home;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "tb_asset_id", length = 128)
    private String tbAssetId;

    @Column(name = "threshold_rain_intensity_close", nullable = false)
    private double thresholdRainIntensityClose = 0;

    @Column(name = "threshold_rain_probability_close", nullable = false)
    private double thresholdRainProbabilityClose = 70;

    @Column(name = "threshold_wind_kph_close", nullable = false)
    private double thresholdWindKphClose = 45;

    @Column(name = "threshold_light_lux_shade", nullable = false)
    private double thresholdLightLuxShade = 55000;

    @Column(name = "threshold_light_lux_release", nullable = false)
    private double thresholdLightLuxRelease = 16000;

    @Column(name = "threshold_indoor_temp_shade_c", nullable = false)
    private double thresholdIndoorTempShadeC = 25;

    @Column(name = "threshold_blinds_shade_position", nullable = false)
    private double thresholdBlindsShadePosition = 85;

    @Column(name = "threshold_blinds_release_position", nullable = false)
    private double thresholdBlindsReleasePosition = 20;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WindowDevice> devices = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Room() {
    }

    public Room(Home home, String name) {
        this(home, name, null);
    }

    public Room(Home home, String name, String tbAssetId) {
        this.home = home;
        this.name = name;
        this.tbAssetId = tbAssetId;
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

    public void addDevice(WindowDevice device) {
        devices.add(device);
        device.assignRoom(this);
    }

    public void removeDevice(WindowDevice device) {
        devices.remove(device);
    }

    public void rename(String name) {
        this.name = name;
    }

    public void updateThingsBoardAsset(String tbAssetId) {
        this.tbAssetId = tbAssetId;
    }

    public UUID getId() {
        return id;
    }

    public Home getHome() {
        return home;
    }

    public String getName() {
        return name;
    }

    public String getTbAssetId() {
        return tbAssetId;
    }

    public List<WindowDevice> getDevices() {
        return devices;
    }

    public double getThresholdRainIntensityClose() {
        return thresholdRainIntensityClose;
    }

    public double getThresholdRainProbabilityClose() {
        return thresholdRainProbabilityClose;
    }

    public double getThresholdWindKphClose() {
        return thresholdWindKphClose;
    }

    public double getThresholdLightLuxShade() {
        return thresholdLightLuxShade;
    }

    public double getThresholdLightLuxRelease() {
        return thresholdLightLuxRelease;
    }

    public double getThresholdIndoorTempShadeC() {
        return thresholdIndoorTempShadeC;
    }

    public double getThresholdBlindsShadePosition() {
        return thresholdBlindsShadePosition;
    }

    public double getThresholdBlindsReleasePosition() {
        return thresholdBlindsReleasePosition;
    }

    public void updateThresholds(
            double rainIntensityClose,
            double rainProbabilityClose,
            double windKphClose,
            double lightLuxShade,
            double lightLuxRelease,
            double indoorTempShadeC,
            double blindsShadePosition,
            double blindsReleasePosition
    ) {
        this.thresholdRainIntensityClose = clamp(rainIntensityClose, 0, 100);
        this.thresholdRainProbabilityClose = clamp(rainProbabilityClose, 0, 100);
        this.thresholdWindKphClose = clamp(windKphClose, 0, 250);
        this.thresholdLightLuxShade = clamp(lightLuxShade, 0, 120000);
        this.thresholdLightLuxRelease = clamp(lightLuxRelease, 0, 120000);
        this.thresholdIndoorTempShadeC = clamp(indoorTempShadeC, -30, 80);
        this.thresholdBlindsShadePosition = clamp(blindsShadePosition, 0, 100);
        this.thresholdBlindsReleasePosition = clamp(blindsReleasePosition, 0, 100);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }

        return Math.min(max, Math.max(min, value));
    }
}
