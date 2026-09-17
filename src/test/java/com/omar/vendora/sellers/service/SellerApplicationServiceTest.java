package com.omar.vendora.sellers.service;

import com.omar.vendora.common.exception.SellerApplicationAlreadyDecidedException;
import com.omar.vendora.common.exception.SellerApplicationAlreadyPendingException;
import com.omar.vendora.common.exception.SellerApplicationNotFoundException;
import com.omar.vendora.common.exception.UserNotFoundException;
import com.omar.vendora.notifications.service.NotificationService;
import com.omar.vendora.sellers.domain.ApplicationStatus;
import com.omar.vendora.sellers.domain.SellerApplication;
import com.omar.vendora.sellers.domain.SellerProfile;
import com.omar.vendora.sellers.dto.SellerApplicationResponse;
import com.omar.vendora.sellers.dto.SubmitSellerApplicationRequest;
import com.omar.vendora.sellers.repository.SellerApplicationRepository;
import com.omar.vendora.sellers.repository.SellerProfileRepository;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerApplicationServiceTest {

    @Mock
    private SellerApplicationRepository sellerApplicationRepository;

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SellerApplicationServiceImpl sellerApplicationService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new User("customer@example.com", "hashed_password", "Customer Name", "+1234567890");
        ReflectionTestUtils.setField(testUser, "id", userId);
    }

    @Test
    @DisplayName("submitApplication - Success with business name and notes")
    void submitApplication_success_withNotes() {
        SubmitSellerApplicationRequest request = new SubmitSellerApplicationRequest("Acme Corp", "We sell gadgets");

        when(sellerApplicationRepository.existsByUserIdAndStatus(userId, ApplicationStatus.PENDING))
            .thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(sellerApplicationRepository.saveAndFlush(any(SellerApplication.class))).thenAnswer(invocation -> {
            SellerApplication app = invocation.getArgument(0);
            ReflectionTestUtils.setField(app, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(app, "createdAt", Instant.now());
            return app;
        });

        SellerApplicationResponse response = sellerApplicationService.submitApplication(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.businessName()).isEqualTo("Acme Corp");
        assertThat(response.notes()).isEqualTo("We sell gadgets");
        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.decidedAt()).isNull();
        assertThat(response.decidedByAdminId()).isNull();

        verify(sellerApplicationRepository, times(1)).saveAndFlush(any(SellerApplication.class));
    }

    @Test
    @DisplayName("submitApplication - Success with null notes")
    void submitApplication_success_withNullNotes() {
        SubmitSellerApplicationRequest request = new SubmitSellerApplicationRequest("Acme Corp", null);

        when(sellerApplicationRepository.existsByUserIdAndStatus(userId, ApplicationStatus.PENDING))
            .thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(sellerApplicationRepository.saveAndFlush(any(SellerApplication.class))).thenAnswer(invocation -> {
            SellerApplication app = invocation.getArgument(0);
            ReflectionTestUtils.setField(app, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(app, "createdAt", Instant.now());
            return app;
        });

        SellerApplicationResponse response = sellerApplicationService.submitApplication(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.businessName()).isEqualTo("Acme Corp");
        assertThat(response.notes()).isNull();
        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);

        verify(sellerApplicationRepository, times(1)).saveAndFlush(any(SellerApplication.class));
    }

    @Test
    @DisplayName("submitApplication - Rejects when user already has a pending application")
    void submitApplication_duplicatePendingPreCheck_throwsException() {
        SubmitSellerApplicationRequest request = new SubmitSellerApplicationRequest("Acme Corp", "Notes");

        when(sellerApplicationRepository.existsByUserIdAndStatus(userId, ApplicationStatus.PENDING))
            .thenReturn(true);

        assertThatThrownBy(() -> sellerApplicationService.submitApplication(userId, request))
            .isInstanceOf(SellerApplicationAlreadyPendingException.class)
            .hasMessageContaining(userId.toString());

        verify(userRepository, never()).findById(any());
        verify(sellerApplicationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("submitApplication - Translates DataIntegrityViolationException on race condition to SellerApplicationAlreadyPendingException")
    void submitApplication_dataIntegrityViolationRace_throwsSellerApplicationAlreadyPendingException() {
        SubmitSellerApplicationRequest request = new SubmitSellerApplicationRequest("Acme Corp", "Notes");

        when(sellerApplicationRepository.existsByUserIdAndStatus(userId, ApplicationStatus.PENDING))
            .thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(sellerApplicationRepository.saveAndFlush(any(SellerApplication.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint idx_seller_applications_user_pending"));

        assertThatThrownBy(() -> sellerApplicationService.submitApplication(userId, request))
            .isInstanceOf(SellerApplicationAlreadyPendingException.class)
            .hasMessageContaining(userId.toString());

        verify(sellerApplicationRepository, times(1)).saveAndFlush(any(SellerApplication.class));
    }

    @Test
    @DisplayName("submitApplication - Throws UserNotFoundException when user does not exist")
    void submitApplication_userNotFound_throwsException() {
        SubmitSellerApplicationRequest request = new SubmitSellerApplicationRequest("Acme Corp", "Notes");

        when(sellerApplicationRepository.existsByUserIdAndStatus(userId, ApplicationStatus.PENDING))
            .thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerApplicationService.submitApplication(userId, request))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining(userId.toString());

        verify(sellerApplicationRepository, never()).saveAndFlush(any());
    }

    // =========================================================================
    // getMyApplication Tests
    // =========================================================================

    @Test
    @DisplayName("getMyApplication - Success: returns latest application response for authenticated user")
    void getMyApplication_success_returnsApplicationResponse() {
        UUID applicationId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        SellerApplication application = new SellerApplication(testUser, "Acme Store", "Seller notes");
        ReflectionTestUtils.setField(application, "id", applicationId);
        ReflectionTestUtils.setField(application, "createdAt", createdAt);

        when(sellerApplicationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(Optional.of(application));

        SellerApplicationResponse response = sellerApplicationService.getMyApplication(userId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(applicationId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.businessName()).isEqualTo("Acme Store");
        assertThat(response.notes()).isEqualTo("Seller notes");
        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.decidedAt()).isNull();
        assertThat(response.decidedByAdminId()).isNull();

        verify(sellerApplicationRepository, times(1)).findFirstByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    @DisplayName("getMyApplication - Throws SellerApplicationNotFoundException when user has no application")
    void getMyApplication_notFound_throwsSellerApplicationNotFoundException() {
        when(sellerApplicationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerApplicationService.getMyApplication(userId))
            .isInstanceOf(SellerApplicationNotFoundException.class)
            .hasMessageContaining(userId.toString());

        verify(sellerApplicationRepository, times(1)).findFirstByUserIdOrderByCreatedAtDesc(userId);
    }

    // =========================================================================
    // approveApplication Tests
    // =========================================================================

    @Test
    @DisplayName("approveApplication - Happy path: transitions to APPROVED, enables seller on User, creates SellerProfile, notifies applicant")
    void approveApplication_success() {
        UUID applicationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User adminUser = new User("admin@example.com", "hash", "Admin Reviewer", "+1987654321");
        ReflectionTestUtils.setField(adminUser, "id", adminId);
        adminUser.setAdmin(true);

        SellerApplication application = new SellerApplication(testUser, "Acme Store", "Notes");
        ReflectionTestUtils.setField(application, "id", applicationId);
        ReflectionTestUtils.setField(application, "createdAt", Instant.now());

        when(sellerApplicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(sellerApplicationRepository.save(any(SellerApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        SellerApplicationResponse response = sellerApplicationService.approveApplication(applicationId, adminId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(response.decidedByAdminId()).isEqualTo(adminId);
        assertThat(response.decidedAt()).isNotNull();

        assertThat(testUser.isSeller()).isTrue();
        verify(userRepository, times(1)).save(testUser);

        ArgumentCaptor<SellerProfile> profileCaptor = ArgumentCaptor.forClass(SellerProfile.class);
        verify(sellerProfileRepository, times(1)).save(profileCaptor.capture());
        SellerProfile createdProfile = profileCaptor.getValue();
        assertThat(createdProfile.getUser()).isEqualTo(testUser);
        assertThat(createdProfile.getDisplayName()).isEqualTo("Acme Store");

        verify(notificationService, times(1)).notify(
            eq(userId),
            eq("Seller Application Approved"),
            any(String.class)
        );
    }

    @Test
    @DisplayName("approveApplication - Throws SellerApplicationNotFoundException when application does not exist")
    void approveApplication_notFound_throwsException() {
        UUID applicationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(sellerApplicationRepository.findById(applicationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerApplicationService.approveApplication(applicationId, adminId))
            .isInstanceOf(SellerApplicationNotFoundException.class)
            .hasMessageContaining(applicationId.toString());

        verify(userRepository, never()).findById(any());
        verify(sellerProfileRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any());
    }

    @Test
    @DisplayName("approveApplication - Guarded state: throws SellerApplicationAlreadyDecidedException when already APPROVED")
    void approveApplication_alreadyApproved_throwsException() {
        UUID applicationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User adminUser = new User("admin@example.com", "hash", "Admin Reviewer", "+1987654321");

        SellerApplication application = new SellerApplication(testUser, "Acme Store", "Notes");
        ReflectionTestUtils.setField(application, "id", applicationId);
        application.approve(adminUser);

        when(sellerApplicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> sellerApplicationService.approveApplication(applicationId, adminId))
            .isInstanceOf(SellerApplicationAlreadyDecidedException.class)
            .hasMessageContaining(applicationId.toString());

        verify(userRepository, never()).findById(any());
        verify(sellerProfileRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any());
    }

    @Test
    @DisplayName("approveApplication - Guarded state: throws SellerApplicationAlreadyDecidedException when already REJECTED")
    void approveApplication_alreadyRejected_throwsException() {
        UUID applicationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User adminUser = new User("admin@example.com", "hash", "Admin Reviewer", "+1987654321");

        SellerApplication application = new SellerApplication(testUser, "Acme Store", "Notes");
        ReflectionTestUtils.setField(application, "id", applicationId);
        application.reject(adminUser);

        when(sellerApplicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> sellerApplicationService.approveApplication(applicationId, adminId))
            .isInstanceOf(SellerApplicationAlreadyDecidedException.class)
            .hasMessageContaining(applicationId.toString());

        verify(userRepository, never()).findById(any());
        verify(sellerProfileRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any());
    }

    @Test
    @DisplayName("approveApplication - Throws UserNotFoundException when admin user is not found")
    void approveApplication_adminNotFound_throwsException() {
        UUID applicationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        SellerApplication application = new SellerApplication(testUser, "Acme Store", "Notes");
        ReflectionTestUtils.setField(application, "id", applicationId);

        when(sellerApplicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(userRepository.findById(adminId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerApplicationService.approveApplication(applicationId, adminId))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining(adminId.toString());

        verify(sellerProfileRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any());
    }

    // =========================================================================
    // rejectApplication Tests
    // =========================================================================

    @Test
    @DisplayName("rejectApplication - Happy path: transitions to REJECTED, does NOT create SellerProfile, does NOT enable seller, notifies applicant")
    void rejectApplication_success() {
        UUID applicationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User adminUser = new User("admin@example.com", "hash", "Admin Reviewer", "+1987654321");
        ReflectionTestUtils.setField(adminUser, "id", adminId);
        adminUser.setAdmin(true);

        SellerApplication application = new SellerApplication(testUser, "Acme Store", "Notes");
        ReflectionTestUtils.setField(application, "id", applicationId);
        ReflectionTestUtils.setField(application, "createdAt", Instant.now());

        when(sellerApplicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(sellerApplicationRepository.save(any(SellerApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        SellerApplicationResponse response = sellerApplicationService.rejectApplication(applicationId, adminId, "Incomplete documentation");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(response.decidedByAdminId()).isEqualTo(adminId);
        assertThat(response.decidedAt()).isNotNull();

        assertThat(testUser.isSeller()).isFalse();
        verify(userRepository, never()).save(testUser);
        verify(sellerProfileRepository, never()).save(any());

        verify(notificationService, times(1)).notify(
            eq(userId),
            eq("Seller Application Rejected"),
            org.mockito.ArgumentMatchers.contains("Incomplete documentation")
        );
    }

    @Test
    @DisplayName("rejectApplication - Guarded state: throws SellerApplicationAlreadyDecidedException when already REJECTED")
    void rejectApplication_alreadyRejected_throwsException() {
        UUID applicationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User adminUser = new User("admin@example.com", "hash", "Admin Reviewer", "+1987654321");

        SellerApplication application = new SellerApplication(testUser, "Acme Store", "Notes");
        ReflectionTestUtils.setField(application, "id", applicationId);
        application.reject(adminUser);

        when(sellerApplicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> sellerApplicationService.rejectApplication(applicationId, adminId, "Reason"))
            .isInstanceOf(SellerApplicationAlreadyDecidedException.class)
            .hasMessageContaining(applicationId.toString());

        verify(sellerProfileRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any());
    }
}
