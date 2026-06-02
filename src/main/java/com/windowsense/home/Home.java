package com.windowsense.home;

import com.windowsense.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "home",
        uniqueConstraints = @UniqueConstraint(name = "uq_home_user_name", columnNames = {"app_user_id", "name"})
)
public class Home {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "tb_customer_id", length = 128)
    private String tbCustomerId;

    @Column(name = "tb_asset_id", length = 128)
    private String tbAssetId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Home() {
    }

    public Home(AppUser appUser, String name) {
        this.appUser = appUser;
        this.name = name;
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

    public UUID getId() {
        return id;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public String getName() {
        return name;
    }

    public String getTbCustomerId() {
        return tbCustomerId;
    }

    public String getTbAssetId() {
        return tbAssetId;
    }
}
