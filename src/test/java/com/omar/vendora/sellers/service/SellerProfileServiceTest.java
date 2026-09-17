package com.omar.vendora.sellers.service;

import com.omar.vendora.common.exception.SellerNotFoundException;
import com.omar.vendora.sellers.domain.SellerProfile;
import com.omar.vendora.sellers.domain.SellerProfileStatus;
import com.omar.vendora.sellers.dto.SellerProfileResponse;
import com.omar.vendora.sellers.repository.SellerProfileRepository;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.domain.UserStatus;
import com.omar.vendora.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerProfileServiceTest {

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SellerProfileServiceImpl sellerProfileService;

    private User testUser;
    private SellerProfile activeProfile;
    private UUID profileId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        profileId = UUID.randomUUID();

        testUser = new User("seller@example.com", "hash123", "Test Seller", "+1234567890");
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setSeller(true);
        ReflectionTestUtils.setField(testUser, "id", userId);

        activeProfile = new SellerProfile(testUser, "Test Store", SellerProfileStatus.ACTIVE, Instant.now());
        ReflectionTestUtils.setField(activeProfile, "id", profileId);
    }

    @Test
    @DisplayName("suspendSeller - successfully suspends active seller and revokes seller capability without altering User.status")
    void suspendSeller_success() {
        when(sellerProfileRepository.findById(profileId)).thenReturn(Optional.of(activeProfile));
        when(sellerProfileRepository.save(any(SellerProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SellerProfileResponse response = sellerProfileService.suspendSeller(profileId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(profileId);
        assertThat(response.status()).isEqualTo(SellerProfileStatus.SUSPENDED);
        assertThat(activeProfile.getStatus()).isEqualTo(SellerProfileStatus.SUSPENDED);
        assertThat(testUser.isSeller()).isFalse();
        assertThat(testUser.getStatus()).isEqualTo(UserStatus.ACTIVE); // User.status untouched

        verify(sellerProfileRepository).save(activeProfile);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("suspendSeller - seller profile not found throws SellerNotFoundException")
    void suspendSeller_notFound_throwsException() {
        UUID nonExistentId = UUID.randomUUID();
        when(sellerProfileRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerProfileService.suspendSeller(nonExistentId))
            .isInstanceOf(SellerNotFoundException.class)
            .hasMessageContaining(nonExistentId.toString());

        verify(sellerProfileRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("suspendSeller - already suspended seller throws IllegalStateException")
    void suspendSeller_alreadySuspended_throwsIllegalStateException() {
        activeProfile.setStatus(SellerProfileStatus.SUSPENDED);
        when(sellerProfileRepository.findById(profileId)).thenReturn(Optional.of(activeProfile));

        assertThatThrownBy(() -> sellerProfileService.suspendSeller(profileId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Seller profile is already suspended");

        verify(sellerProfileRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("reactivateSeller - successfully reactivates suspended seller and restores seller capability without altering User.status")
    void reactivateSeller_success() {
        activeProfile.setStatus(SellerProfileStatus.SUSPENDED);
        testUser.setSeller(false);

        when(sellerProfileRepository.findById(profileId)).thenReturn(Optional.of(activeProfile));
        when(sellerProfileRepository.save(any(SellerProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SellerProfileResponse response = sellerProfileService.reactivateSeller(profileId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(profileId);
        assertThat(response.status()).isEqualTo(SellerProfileStatus.ACTIVE);
        assertThat(activeProfile.getStatus()).isEqualTo(SellerProfileStatus.ACTIVE);
        assertThat(testUser.isSeller()).isTrue();
        assertThat(testUser.getStatus()).isEqualTo(UserStatus.ACTIVE); // User.status untouched

        verify(sellerProfileRepository).save(activeProfile);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("reactivateSeller - seller profile not found throws SellerNotFoundException")
    void reactivateSeller_notFound_throwsException() {
        UUID nonExistentId = UUID.randomUUID();
        when(sellerProfileRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerProfileService.reactivateSeller(nonExistentId))
            .isInstanceOf(SellerNotFoundException.class)
            .hasMessageContaining(nonExistentId.toString());

        verify(sellerProfileRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("reactivateSeller - already active seller throws IllegalStateException")
    void reactivateSeller_alreadyActive_throwsIllegalStateException() {
        when(sellerProfileRepository.findById(profileId)).thenReturn(Optional.of(activeProfile));

        assertThatThrownBy(() -> sellerProfileService.reactivateSeller(profileId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Seller profile is already active");

        verify(sellerProfileRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }
}
