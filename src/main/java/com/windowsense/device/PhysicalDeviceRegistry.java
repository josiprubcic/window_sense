package com.windowsense.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "physical_device_registry")
public class PhysicalDeviceRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "serial_number", nullable = false, unique = true, length = 128)
    private String serialNumber;

    @Column(name = "pairing_code_hash", nullable = false, unique = true, length = 64)
    private String pairingCodeHash;

    @Column(name = "tb_device_id", nullable = false, length = 128)
    private String tbDeviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhysicalDeviceRegistryStatus status;

    @Column(name = "claimed_by_user_id")
    private UUID claimedByUserId;

    @Column(name = "claimed_room_id")
    private UUID claimedRoomId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected PhysicalDeviceRegistry() {
    }

    public PhysicalDeviceRegistry(String serialNumber, String pairingCodeHash, String tbDeviceId, PhysicalDeviceRegistryStatus status) {
        this.serialNumber = serialNumber;
        this.pairingCodeHash = pairingCodeHash;
        this.tbDeviceId = tbDeviceId;
        this.status = status;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void claim(UUID userId, UUID roomId) {
        if (status != PhysicalDeviceRegistryStatus.AVAILABLE) {
            throw new IllegalStateException("Uredjaj nije dostupan za povezivanje.");
        }

        status = PhysicalDeviceRegistryStatus.CLAIMED;
        claimedByUserId = userId;
        claimedRoomId = roomId;
        claimedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getPairingCodeHash() {
        return pairingCodeHash;
    }

    public String getTbDeviceId() {
        return tbDeviceId;
    }

    public PhysicalDeviceRegistryStatus getStatus() {
        return status;
    }

    public UUID getClaimedByUserId() {
        return claimedByUserId;
    }

    public UUID getClaimedRoomId() {
        return claimedRoomId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
