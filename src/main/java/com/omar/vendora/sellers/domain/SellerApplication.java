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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "seller_applications")
public class SellerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_admin_id")
    private User decidedByAdmin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SellerApplication() {
    }

    public SellerApplication(User user, String businessName, String notes) {
        this.user = user;
        this.businessName = businessName;
        this.notes = notes;
        this.status = ApplicationStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = ApplicationStatus.PENDING;
        }
    }

    public void approve(User admin) {
        if (this.status != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Cannot approve seller application with status: " + this.status);
        }
        Objects.requireNonNull(admin, "Admin must not be null");
        this.status = ApplicationStatus.APPROVED;
        this.decidedByAdmin = admin;
        this.decidedAt = Instant.now();
    }

    public void reject(User admin) {
        if (this.status != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Cannot reject seller application with status: " + this.status);
        }
        Objects.requireNonNull(admin, "Admin must not be null");
        this.status = ApplicationStatus.REJECTED;
        this.decidedByAdmin = admin;
        this.decidedAt = Instant.now();
    }

    public boolean isPending() {
        return this.status == ApplicationStatus.PENDING;
    }

    public boolean isApproved() {
        return this.status == ApplicationStatus.APPROVED;
    }

    public boolean isRejected() {
        return this.status == ApplicationStatus.REJECTED;
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

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        if (this.status == status) {
            return;
        }
        if (this.status != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Cannot change status from terminal state: " + this.status);
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.status = status;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public User getDecidedByAdmin() {
        return decidedByAdmin;
    }

    public void setDecidedByAdmin(User decidedByAdmin) {
        this.decidedByAdmin = decidedByAdmin;
    }

    public UUID getDecidedByAdminId() {
        return decidedByAdmin != null ? decidedByAdmin.getId() : null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
