package com.omar.vendora.sellers.domain;

import com.omar.vendora.users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerApplicationTest {

    private User applicant;
    private User admin;

    @BeforeEach
    void setUp() {
        applicant = new User("applicant@example.com", "hash123", "Applicant User", "+1234567890");
        admin = new User("admin@example.com", "hash123", "Admin User", "+1234567891");
        admin.setAdmin(true);
    }

    @Test
    @DisplayName("Initial State - Newly constructed application defaults to PENDING")
    void initialState_defaultsToPending() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Looking forward to selling");

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(application.isPending()).isTrue();
        assertThat(application.isApproved()).isFalse();
        assertThat(application.isRejected()).isFalse();
        assertThat(application.getUser()).isEqualTo(applicant);
        assertThat(application.getBusinessName()).isEqualTo("Acme Store");
        assertThat(application.getNotes()).isEqualTo("Looking forward to selling");
        assertThat(application.getDecidedAt()).isNull();
        assertThat(application.getDecidedByAdmin()).isNull();
    }

    @Test
    @DisplayName("Initial State - No-arg constructor defaults to PENDING")
    void initialState_noArgConstructor_defaultsToPending() {
        SellerApplication application = new SellerApplication();

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(application.isPending()).isTrue();
    }

    @Test
    @DisplayName("onCreate - PrePersist initializes createdAt and ensures PENDING status")
    void onCreate_initializesCreatedAtAndStatus() {
        SellerApplication application = new SellerApplication();
        application.onCreate();

        assertThat(application.getCreatedAt()).isNotNull();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("Guarded Transition - approve() from PENDING transitions to APPROVED with decision metadata")
    void approve_fromPending_transitionsToApprovedWithMetadata() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Notes");
        Instant beforeTransition = Instant.now().minusSeconds(1);

        application.approve(admin);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(application.isApproved()).isTrue();
        assertThat(application.isPending()).isFalse();
        assertThat(application.isRejected()).isFalse();
        assertThat(application.getDecidedByAdmin()).isEqualTo(admin);
        assertThat(application.getDecidedAt()).isNotNull();
        assertThat(application.getDecidedAt()).isAfterOrEqualTo(beforeTransition);
    }

    @Test
    @DisplayName("Guarded Transition - reject() from PENDING transitions to REJECTED with decision metadata")
    void reject_fromPending_transitionsToRejectedWithMetadata() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Notes");
        Instant beforeTransition = Instant.now().minusSeconds(1);

        application.reject(admin);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(application.isRejected()).isTrue();
        assertThat(application.isPending()).isFalse();
        assertThat(application.isApproved()).isFalse();
        assertThat(application.getDecidedByAdmin()).isEqualTo(admin);
        assertThat(application.getDecidedAt()).isNotNull();
        assertThat(application.getDecidedAt()).isAfterOrEqualTo(beforeTransition);
    }

    @Test
    @DisplayName("Guarded Transition - approve() on already APPROVED application throws IllegalStateException")
    void approve_whenAlreadyApproved_throwsIllegalStateException() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Notes");
        application.approve(admin);

        assertThatThrownBy(() -> application.approve(admin))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot approve seller application with status: APPROVED");
    }

    @Test
    @DisplayName("Guarded Transition - reject() on already APPROVED application throws IllegalStateException")
    void reject_whenAlreadyApproved_throwsIllegalStateException() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Notes");
        application.approve(admin);

        assertThatThrownBy(() -> application.reject(admin))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot reject seller application with status: APPROVED");
    }

    @Test
    @DisplayName("Guarded Transition - approve() on already REJECTED application throws IllegalStateException")
    void approve_whenAlreadyRejected_throwsIllegalStateException() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Notes");
        application.reject(admin);

        assertThatThrownBy(() -> application.approve(admin))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot approve seller application with status: REJECTED");
    }

    @Test
    @DisplayName("Guarded Transition - reject() on already REJECTED application throws IllegalStateException")
    void reject_whenAlreadyRejected_throwsIllegalStateException() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Notes");
        application.reject(admin);

        assertThatThrownBy(() -> application.reject(admin))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot reject seller application with status: REJECTED");
    }

    @Test
    @DisplayName("Guarded Transition - approve() with null admin throws NullPointerException")
    void approve_withNullAdmin_throwsNullPointerException() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Notes");

        assertThatThrownBy(() -> application.approve(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Admin must not be null");
    }

    @Test
    @DisplayName("Guarded Transition - reject() with null admin throws NullPointerException")
    void reject_withNullAdmin_throwsNullPointerException() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Notes");

        assertThatThrownBy(() -> application.reject(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Admin must not be null");
    }

    @Test
    @DisplayName("setStatus - Guarded against unchecked mutations from terminal state APPROVED")
    void setStatus_whenApproved_throwsIllegalStateException() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Notes");
        application.approve(admin);

        assertThatThrownBy(() -> application.setStatus(ApplicationStatus.REJECTED))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot change status from terminal state: APPROVED");
    }

    @Test
    @DisplayName("setStatus - Guarded against unchecked mutations from terminal state REJECTED")
    void setStatus_whenRejected_throwsIllegalStateException() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Notes");
        application.reject(admin);

        assertThatThrownBy(() -> application.setStatus(ApplicationStatus.APPROVED))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot change status from terminal state: REJECTED");
    }

    @Test
    @DisplayName("setStatus - Setting null status throws IllegalArgumentException")
    void setStatus_null_throwsIllegalArgumentException() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Notes");

        assertThatThrownBy(() -> application.setStatus(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Status cannot be null");
    }

    @Test
    @DisplayName("setStatus - Setting identical status is a no-op")
    void setStatus_sameStatus_isNoOp() {
        SellerApplication application = new SellerApplication(applicant, "Acme Store", "Notes");
        application.setStatus(ApplicationStatus.PENDING);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }
}
