package com.windowsense.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
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

    @Column(name = "hardware_id", unique = true, length = 128)
    private String hardwareId;

    @Column(name = "firmware_version", length = 64)
    private String firmwareVersion;

    @Column(name = "capabilities", columnDefinition = "TEXT")
    private String capabilityLabels;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "physical_device_registry_capabilities",
            joinColumns = @JoinColumn(name = "physical_device_registry_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "capability", nullable = false, length = 40)
    private Set<DeviceCapability> capabilities = new HashSet<>();

    @Column(name = "device_secret_hash", length = 64)
    private String deviceSecretHash;

    @Column(name = "thingsboard_access_token_hash", unique = true, length = 64)
    private String thingsBoardAccessTokenHash;

    @Column(name = "provisioning_session_hash", unique = true, length = 64)
    private String provisioningSessionHash;

    @Column(name = "provisioning_session_expires_at")
    private Instant provisioningSessionExpiresAt;

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

    @Column(name = "pairing_code_consumed_at")
    private Instant pairingCodeConsumedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "bootstrapped_at")
    private Instant bootstrappedAt;

    protected PhysicalDeviceRegistry() {
    }

    public PhysicalDeviceRegistry(String serialNumber, String pairingCodeHash, String tbDeviceId, PhysicalDeviceRegistryStatus status) {
        this.serialNumber = serialNumber;
        this.pairingCodeHash = pairingCodeHash;
        this.tbDeviceId = tbDeviceId;
        this.status = status;
    }

    public void updateProvisioningMetadata(
        String hardwareId,
        String firmwareVersion,
        String capabilityLabels,
        String deviceSecretHash,
        String provisioningSessionHash,
        Instant provisioningSessionExpiresAt
    ) {
        this.hardwareId = blankToNull(hardwareId);
        this.firmwareVersion = blankToNull(firmwareVersion);
        this.capabilityLabels = blankToNull(capabilityLabels);
        replaceCapabilities(DeviceCapabilities.fromCsv(capabilityLabels));
        this.deviceSecretHash = required(deviceSecretHash, "Hash tajnog kljuca uredjaja je obavezan.");
        this.provisioningSessionHash = required(provisioningSessionHash, "Provisioning session hash je obavezan.");
        this.provisioningSessionExpiresAt = provisioningSessionExpiresAt;
    }

    public void updateRegistrationMetadata(String hardwareId, String firmwareVersion, String capabilityLabels) {
        this.hardwareId = blankToNull(hardwareId);
        this.firmwareVersion = blankToNull(firmwareVersion);
        this.capabilityLabels = blankToNull(capabilityLabels);
        replaceCapabilities(DeviceCapabilities.fromCsv(capabilityLabels));
    }

    public void updateRegistrationMetadata(
            String hardwareId,
            String firmwareVersion,
            String capabilityLabels,
            Collection<DeviceCapability> capabilities
    ) {
        this.hardwareId = blankToNull(hardwareId);
        this.firmwareVersion = blankToNull(firmwareVersion);
        this.capabilityLabels = blankToNull(capabilityLabels);
        replaceCapabilities(capabilities);
    }

    public void setThingsBoardAccessTokenHash(String thingsBoardAccessTokenHash) {
        this.thingsBoardAccessTokenHash = required(thingsBoardAccessTokenHash, "Hash ThingsBoard access tokena je obavezan.");
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void claim(UUID userId, UUID roomId) {
        if (status != PhysicalDeviceRegistryStatus.CLAIMABLE) {
            throw new IllegalStateException("Uredjaj nije dostupan za povezivanje.");
        }
        if (pairingCodeConsumedAt != null) {
            throw new IllegalStateException("DEVICE_ALREADY_CLAIMED");
        }

        status = PhysicalDeviceRegistryStatus.CLAIMED;
        claimedByUserId = userId;
        claimedRoomId = roomId;
        claimedAt = Instant.now();
        pairingCodeConsumedAt = claimedAt;
    }

    public boolean matchesProvisioningSession(String provisioningSessionHash) {
        return this.provisioningSessionHash != null
                && this.provisioningSessionHash.equals(provisioningSessionHash)
                && provisioningSessionExpiresAt != null
                && provisioningSessionExpiresAt.isAfter(Instant.now());
    }

    public boolean matchesDeviceSecret(String deviceSecretHash) {
        return this.deviceSecretHash != null && this.deviceSecretHash.equals(deviceSecretHash);
    }

    public void markBootstrapped() {
        bootstrappedAt = Instant.now();
        lastSeenAt = bootstrappedAt;
        provisioningSessionHash = null;
        provisioningSessionExpiresAt = null;
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

    public String getHardwareId() {
        return hardwareId;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public String getCapabilityLabels() {
        return capabilityLabels;
    }

    public Set<DeviceCapability> getCapabilities() {
        if (capabilities == null || capabilities.isEmpty()) {
            return DeviceCapabilities.fromCsv(capabilityLabels);
        }
        return EnumSet.copyOf(capabilities);
    }

    public String getDeviceSecretHash() {
        return deviceSecretHash;
    }

    public String getThingsBoardAccessTokenHash() {
        return thingsBoardAccessTokenHash;
    }

    public String getProvisioningSessionHash() {
        return provisioningSessionHash;
    }

    public Instant getProvisioningSessionExpiresAt() {
        return provisioningSessionExpiresAt;
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

    public Instant getPairingCodeConsumedAt() {
        return pairingCodeConsumedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getBootstrappedAt() {
        return bootstrappedAt;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private void replaceCapabilities(Collection<DeviceCapability> capabilities) {
        this.capabilities.clear();
        if (capabilities == null || capabilities.isEmpty()) {
            this.capabilities.addAll(DeviceCapabilities.combinedDevice());
        } else {
            this.capabilities.addAll(capabilities);
        }
    }
}
