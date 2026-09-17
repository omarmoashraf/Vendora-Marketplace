package com.omar.vendora.sellers.domain;

import com.omar.vendora.users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerProfileTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("seller@example.com", "hash123", "Seller User", "+1234567890");
        user.setSeller(true);
    }

    @Test
    @DisplayName("Initial State - Newly constructed profile defaults to ACTIVE")
    void initialState_defaultsToActive() {
        SellerProfile profile = new SellerProfile(user, "My Store");

        assertThat(profile.getStatus()).isEqualTo(SellerProfileStatus.ACTIVE);
        assertThat(profile.isActive()).isTrue();
        assertThat(profile.isSuspended()).isFalse();
        assertThat(profile.getUser()).isEqualTo(user);
        assertThat(profile.getDisplayName()).isEqualTo("My Store");
        assertThat(profile.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("Initial State - Constructor with all parameters sets values correctly")
    void initialState_allArgs() {
        Instant approvedAt = Instant.parse("2026-01-01T00:00:00Z");
        SellerProfile profile = new SellerProfile(user, "My Store", SellerProfileStatus.SUSPENDED, approvedAt);

        assertThat(profile.getStatus()).isEqualTo(SellerProfileStatus.SUSPENDED);
        assertThat(profile.isActive()).isFalse();
        assertThat(profile.isSuspended()).isTrue();
        assertThat(profile.getApprovedAt()).isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("onCreate - PrePersist initializes approvedAt and status if null")
    void onCreate_initializesDefaults() {
        SellerProfile profile = new SellerProfile();
        profile.setStatus(null);
        profile.setApprovedAt(null);

        profile.onCreate();

        assertThat(profile.getStatus()).isEqualTo(SellerProfileStatus.ACTIVE);
        assertThat(profile.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("suspend - Active profile transitions to SUSPENDED")
    void suspend_fromActive_success() {
        SellerProfile profile = new SellerProfile(user, "My Store");
        assertThat(profile.isActive()).isTrue();

        profile.suspend();

        assertThat(profile.getStatus()).isEqualTo(SellerProfileStatus.SUSPENDED);
        assertThat(profile.isSuspended()).isTrue();
        assertThat(profile.isActive()).isFalse();
    }

    @Test
    @DisplayName("suspend - Already suspended profile throws IllegalStateException")
    void suspend_alreadySuspended_throwsIllegalStateException() {
        SellerProfile profile = new SellerProfile(user, "My Store");
        profile.suspend();

        assertThatThrownBy(profile::suspend)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Seller profile is already suspended");
    }

    @Test
    @DisplayName("reactivate - Suspended profile transitions to ACTIVE")
    void reactivate_fromSuspended_success() {
        SellerProfile profile = new SellerProfile(user, "My Store");
        profile.suspend();
        assertThat(profile.isSuspended()).isTrue();

        profile.reactivate();

        assertThat(profile.getStatus()).isEqualTo(SellerProfileStatus.ACTIVE);
        assertThat(profile.isActive()).isTrue();
        assertThat(profile.isSuspended()).isFalse();
    }

    @Test
    @DisplayName("reactivate - Already active profile throws IllegalStateException")
    void reactivate_alreadyActive_throwsIllegalStateException() {
        SellerProfile profile = new SellerProfile(user, "My Store");

        assertThatThrownBy(profile::reactivate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Seller profile is already active");
    }
}
