package com.omar.vendora.sellers.domain;

import com.omar.vendora.users.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "seller_profiles")
public class SellerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SellerProfileStatus status = SellerProfileStatus.ACTIVE;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "payout_method", length = 50)
    private String payoutMethod;

    @Column(name = "payout_details_json", columnDefinition = "TEXT")
    private String payoutDetailsJson;

    @Column(name = "approved_at", nullable = false, updatable = false)
    private Instant approvedAt;

    public SellerProfile() {
    }

    public SellerProfile(User user, String displayName) {
        this.user = Objects.requireNonNull(user, "User must not be null");
        this.displayName = Objects.requireNonNull(displayName, "Display name must not be null");
        this.status = SellerProfileStatus.ACTIVE;
        this.approvedAt = Instant.now();
    }

    public SellerProfile(User user, String displayName, SellerProfileStatus status, Instant approvedAt) {
        this.user = Objects.requireNonNull(user, "User must not be null");
        this.displayName = Objects.requireNonNull(displayName, "Display name must not be null");
        this.status = (status != null) ? status : SellerProfileStatus.ACTIVE;
        this.approvedAt = (approvedAt != null) ? approvedAt : Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.approvedAt == null) {
            this.approvedAt = Instant.now();
        }
        if (this.status == null) {
            this.status = SellerProfileStatus.ACTIVE;
        }
    }

    public void suspend() {
        if (this.status == SellerProfileStatus.SUSPENDED) {
            throw new IllegalStateException("Seller profile is already suspended");
        }
        this.status = SellerProfileStatus.SUSPENDED;
    }

    public void reactivate() {
        if (this.status == SellerProfileStatus.ACTIVE) {
            throw new IllegalStateException("Seller profile is already active");
        }
        this.status = SellerProfileStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.status == SellerProfileStatus.ACTIVE;
    }

    public boolean isSuspended() {
        return this.status == SellerProfileStatus.SUSPENDED;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public SellerProfileStatus getStatus() {
        return status;
    }

    public void setStatus(SellerProfileStatus status) {
        this.status = status;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPayoutMethod() {
        return payoutMethod;
    }

    public void setPayoutMethod(String payoutMethod) {
        this.payoutMethod = payoutMethod;
    }

    public String getPayoutDetailsJson() {
        return payoutDetailsJson;
    }

    public void setPayoutDetailsJson(String payoutDetailsJson) {
        this.payoutDetailsJson = payoutDetailsJson;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }
}
