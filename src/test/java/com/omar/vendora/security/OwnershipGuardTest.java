package com.omar.vendora.security;

import com.omar.vendora.common.exception.OwnershipViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnershipGuardTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private OwnershipGuard ownershipGuard;

    private UUID currentUserId;
    private UUID resourceOwnerId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        resourceOwnerId = currentUserId; // own resource
        otherUserId = UUID.randomUUID(); // different owner
    }

    @Test
    @DisplayName("checkOwnership succeeds when current user is the resource owner")
    void checkOwnership_whenOwner_succeeds() {
        when(currentUserProvider.isAdmin()).thenReturn(false);
        when(currentUserProvider.getCurrentUserId()).thenReturn(currentUserId);

        assertThatCode(() -> ownershipGuard.checkOwnership(resourceOwnerId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkOwnership throws OwnershipViolationException when user is not owner and not admin")
    void checkOwnership_whenNotOwner_throwsOwnershipViolationException() {
        when(currentUserProvider.isAdmin()).thenReturn(false);
        when(currentUserProvider.getCurrentUserId()).thenReturn(currentUserId);

        assertThatThrownBy(() -> ownershipGuard.checkOwnership(otherUserId))
                .isInstanceOf(OwnershipViolationException.class)
                .hasMessageContaining("Access denied: you do not own this resource");
    }

    @Test
    @DisplayName("checkOwnership allows admin to access any resource without being owner")
    void checkOwnership_whenAdmin_bypassesCheck() {
        when(currentUserProvider.isAdmin()).thenReturn(true);

        assertThatCode(() -> ownershipGuard.checkOwnership(otherUserId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkOwnership with two UUIDs compares them correctly")
    void checkOwnership_twoUuids_checksEquality() {
        when(currentUserProvider.isAdmin()).thenReturn(false);

        assertThatCode(() -> ownershipGuard.checkOwnership(currentUserId, currentUserId))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> ownershipGuard.checkOwnership(currentUserId, otherUserId))
                .isInstanceOf(OwnershipViolationException.class);
    }

    @Test
    @DisplayName("isOwner returns true for owner or admin, and false otherwise")
    void isOwner_behavior() {
        // Admin
        when(currentUserProvider.isAdmin()).thenReturn(true);
        assertThat(ownershipGuard.isOwner(otherUserId)).isTrue();

        // Non-admin owner
        when(currentUserProvider.isAdmin()).thenReturn(false);
        when(currentUserProvider.getCurrentUserIdOptional()).thenReturn(Optional.of(currentUserId));
        assertThat(ownershipGuard.isOwner(currentUserId)).isTrue();

        // Non-admin non-owner
        assertThat(ownershipGuard.isOwner(otherUserId)).isFalse();
    }
}
