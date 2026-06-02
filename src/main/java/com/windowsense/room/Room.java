package com.windowsense.room;

import com.windowsense.device.WindowDevice;
import com.windowsense.home.Home;
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

    @Column(name = "tb_asset_id", nullable = false, length = 128)
    private String tbAssetId;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WindowDevice> devices = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Room() {
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
}
