package com.windowsense.device;

import com.windowsense.room.Room;
import jakarta.persistence.Column;
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

    @Column(name = "physical_hardware_id", unique = true, length = 128)
    private String physicalHardwareId;

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
        return new WindowDevice(name, DeviceType.VIRTUAL, true, DeviceStatus.ACTIVE, tbDeviceId);
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

    public UUID getId() {
        return id;
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

    public String getPhysicalHardwareId() {
        return physicalHardwareId;
    }

    private static void validateVirtualConsistency(DeviceType deviceType, boolean virtual) {
        if (deviceType == DeviceType.VIRTUAL && !virtual) {
            throw new IllegalArgumentException("Virtualni uredjaj mora imati isVirtual=true.");
        }

        if (deviceType == DeviceType.PHYSICAL && virtual) {
            throw new IllegalArgumentException("Fizicki uredjaj mora imati isVirtual=false.");
        }
    }
}
