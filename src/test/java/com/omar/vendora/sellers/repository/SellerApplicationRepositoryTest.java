package com.omar.vendora.sellers.repository;

import com.omar.vendora.identity.repository.RefreshTokenRepository;
import com.omar.vendora.sellers.domain.ApplicationStatus;
import com.omar.vendora.sellers.domain.SellerApplication;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.domain.UserStatus;
import com.omar.vendora.users.repository.AddressRepository;
import com.omar.vendora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SellerApplicationRepositoryTest {

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        sellerApplicationRepository.deleteAll();
        addressRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createAndPersistUser(String email, boolean isAdmin) {
        User user = new User(email, "hashed_pw_secret_123", "Test User", "+1234567890");
        user.setStatus(UserStatus.ACTIVE);
        user.setAdmin(isAdmin);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("save - Happy path: persists SellerApplication with user, businessName, notes and PENDING status")
    void save_happyPath_persistsSellerApplication() {
        User user = createAndPersistUser("applicant1@example.com", false);

        SellerApplication application = new SellerApplication(user, "Vintage Treasures", "We sell handmade goods");
        SellerApplication saved = sellerApplicationRepository.saveAndFlush(application);
        entityManager.clear();

        assertThat(saved.getId()).isNotNull();

        Optional<SellerApplication> foundOpt = sellerApplicationRepository.findById(saved.getId());
        assertThat(foundOpt).isPresent();

        SellerApplication found = foundOpt.get();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getBusinessName()).isEqualTo("Vintage Treasures");
        assertThat(found.getNotes()).isEqualTo("We sell handmade goods");
        assertThat(found.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getDecidedAt()).isNull();
        assertThat(found.getDecidedByAdmin()).isNull();
    }

    @Test
    @DisplayName("save - Default values: status defaults to PENDING and createdAt is auto-generated")
    void save_defaultValues_defaultsToPendingAndAutoCreatedAt() {
        User user = createAndPersistUser("applicant_defaults@example.com", false);

        SellerApplication application = new SellerApplication();
        application.setUser(user);
        application.setBusinessName("Default Shop");

        SellerApplication saved = sellerApplicationRepository.saveAndFlush(application);
        entityManager.clear();

        SellerApplication found = sellerApplicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getNotes()).isNull();
        assertThat(found.getDecidedAt()).isNull();
        assertThat(found.getDecidedByAdmin()).isNull();
    }

    @Test
    @DisplayName("save - Guarded transition to APPROVED persists decision metadata")
    void save_approvedApplication_persistsDecisionMetadata() {
        User user = createAndPersistUser("applicant_appr@example.com", false);
        User admin = createAndPersistUser("admin_appr@example.com", true);

        SellerApplication application = new SellerApplication(user, "Approved Shop", "All documents submitted");
        application.approve(admin);

        SellerApplication saved = sellerApplicationRepository.saveAndFlush(application);
        entityManager.clear();

        SellerApplication found = sellerApplicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(found.getDecidedAt()).isNotNull();
        assertThat(found.getDecidedByAdmin()).isNotNull();
        assertThat(found.getDecidedByAdmin().getId()).isEqualTo(admin.getId());
    }

    @Test
    @DisplayName("save - Guarded transition to REJECTED persists decision metadata")
    void save_rejectedApplication_persistsDecisionMetadata() {
        User user = createAndPersistUser("applicant_rej@example.com", false);
        User admin = createAndPersistUser("admin_rej@example.com", true);

        SellerApplication application = new SellerApplication(user, "Rejected Shop", "Incomplete documentation");
        application.reject(admin);

        SellerApplication saved = sellerApplicationRepository.saveAndFlush(application);
        entityManager.clear();

        SellerApplication found = sellerApplicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(found.getDecidedAt()).isNotNull();
        assertThat(found.getDecidedByAdmin()).isNotNull();
        assertThat(found.getDecidedByAdmin().getId()).isEqualTo(admin.getId());
    }

    @Test
    @DisplayName("save - Null user violates foreign key / non-null constraint")
    void save_nullUser_throwsDataIntegrityViolationException() {
        SellerApplication application = new SellerApplication(null, "No User Shop", "Notes");

        assertThatThrownBy(() -> sellerApplicationRepository.saveAndFlush(application))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("save - Null businessName violates non-null constraint")
    void save_nullBusinessName_throwsDataIntegrityViolationException() {
        User user = createAndPersistUser("applicant_null_bn@example.com", false);
        SellerApplication application = new SellerApplication(user, null, "Notes");

        assertThatThrownBy(() -> sellerApplicationRepository.saveAndFlush(application))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Referential integrity - Deleting applicant user cascades and removes their applications")
    void cascadeDelete_deletingUserRemovesApplications() {
        User user = createAndPersistUser("cascade_user@example.com", false);
        SellerApplication application = new SellerApplication(user, "Cascade Store", null);
        SellerApplication saved = sellerApplicationRepository.saveAndFlush(application);
        UUID applicationId = saved.getId();

        entityManager.clear();

        userRepository.deleteById(user.getId());
        userRepository.flush();
        entityManager.clear();

        assertThat(sellerApplicationRepository.findById(applicationId)).isEmpty();
    }

    @Test
    @DisplayName("Referential integrity - Deleting admin sets decided_by_admin_id to NULL preserving historical application")
    void referentialIntegrity_deletingAdminSetsDecidedByAdminToNull() {
        User user = createAndPersistUser("applicant_audit@example.com", false);
        User admin = createAndPersistUser("admin_audit@example.com", true);

        SellerApplication application = new SellerApplication(user, "Audit Store", "Notes");
        application.approve(admin);
        SellerApplication saved = sellerApplicationRepository.saveAndFlush(application);
        UUID applicationId = saved.getId();

        entityManager.clear();

        // Delete the admin user
        userRepository.deleteById(admin.getId());
        userRepository.flush();
        entityManager.clear();

        // The application must still exist, with decidedByAdmin set to null
        Optional<SellerApplication> foundOpt = sellerApplicationRepository.findById(applicationId);
        assertThat(foundOpt).isPresent();
        SellerApplication found = foundOpt.get();
        assertThat(found.getDecidedByAdmin()).isNull();
        assertThat(found.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(found.getDecidedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("Lazy loading - User and decidedByAdmin relationships are lazily fetched")
    void lazyLoading_relationshipsAreLazy() {
        User user = createAndPersistUser("lazy_applicant@example.com", false);
        User admin = createAndPersistUser("lazy_admin@example.com", true);

        SellerApplication application = new SellerApplication(user, "Lazy Shop", "Lazy loading test");
        application.approve(admin);
        SellerApplication saved = sellerApplicationRepository.saveAndFlush(application);

        entityManager.flush();
        entityManager.clear();

        SellerApplication loaded = sellerApplicationRepository.findById(saved.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getUser())).isFalse();
        assertThat(Hibernate.isInitialized(loaded.getDecidedByAdmin())).isFalse();

        assertThat(loaded.getUser().getEmail()).isEqualTo("lazy_applicant@example.com");
        assertThat(Hibernate.isInitialized(loaded.getUser())).isTrue();

        assertThat(loaded.getDecidedByAdmin().getEmail()).isEqualTo("lazy_admin@example.com");
        assertThat(Hibernate.isInitialized(loaded.getDecidedByAdmin())).isTrue();
    }

    // ==========================================
    // Partial Unique Index Tests
    // ==========================================

    @Test
    @DisplayName("Database Constraint - Inserting two PENDING applications for same user violates partial unique index")
    void databaseConstraint_twoPendingApplicationsForSameUser_throwsDataIntegrityViolationException() {
        User user = createAndPersistUser("double_pending@example.com", false);

        SellerApplication app1 = new SellerApplication(user, "Shop 1", "First pending submission");
        sellerApplicationRepository.saveAndFlush(app1);

        SellerApplication app2 = new SellerApplication(user, "Shop 2", "Second pending submission");

        assertThatThrownBy(() -> sellerApplicationRepository.saveAndFlush(app2))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Database Constraint - Multiple non-pending applications and one pending application for same user succeeds")
    void databaseConstraint_multipleNonPendingAndOnePending_succeeds() {
        User user = createAndPersistUser("multi_status@example.com", false);
        User admin = createAndPersistUser("admin_multi@example.com", true);

        // Previous application was rejected
        SellerApplication app1 = new SellerApplication(user, "Shop Attempt 1", "First submission");
        app1.reject(admin);
        sellerApplicationRepository.saveAndFlush(app1);

        // New application in PENDING is allowed
        SellerApplication app2 = new SellerApplication(user, "Shop Attempt 2", "Second submission");
        sellerApplicationRepository.saveAndFlush(app2);
        entityManager.clear();

        List<SellerApplication> userApps = sellerApplicationRepository.findAllByUserId(user.getId());
        assertThat(userApps).hasSize(2);
    }

    @Test
    @DisplayName("Database Constraint - Different users can each have a PENDING application concurrently")
    void databaseConstraint_differentUsersCanEachHavePendingApplication_succeeds() {
        User userA = createAndPersistUser("userA_pending@example.com", false);
        User userB = createAndPersistUser("userB_pending@example.com", false);

        SellerApplication appA = new SellerApplication(userA, "Shop A", "Notes A");
        SellerApplication appB = new SellerApplication(userB, "Shop B", "Notes B");

        sellerApplicationRepository.saveAndFlush(appA);
        sellerApplicationRepository.saveAndFlush(appB);
        entityManager.clear();

        assertThat(sellerApplicationRepository.findByUserIdAndStatus(userA.getId(), ApplicationStatus.PENDING)).isPresent();
        assertThat(sellerApplicationRepository.findByUserIdAndStatus(userB.getId(), ApplicationStatus.PENDING)).isPresent();
    }

    // ==========================================
    // Repository Query Method Tests
    // ==========================================

    @Test
    @DisplayName("existsByUserIdAndStatus - Correctly detects presence or absence of application by status")
    void existsByUserIdAndStatus_detectsPresenceCorrectly() {
        User user = createAndPersistUser("exists_query@example.com", false);

        assertThat(sellerApplicationRepository.existsByUserIdAndStatus(user.getId(), ApplicationStatus.PENDING)).isFalse();

        SellerApplication app = new SellerApplication(user, "Existing Shop", "Notes");
        sellerApplicationRepository.saveAndFlush(app);
        entityManager.clear();

        assertThat(sellerApplicationRepository.existsByUserIdAndStatus(user.getId(), ApplicationStatus.PENDING)).isTrue();
        assertThat(sellerApplicationRepository.existsByUserIdAndStatus(user.getId(), ApplicationStatus.APPROVED)).isFalse();
        assertThat(sellerApplicationRepository.existsByUserIdAndStatus(user.getId(), ApplicationStatus.REJECTED)).isFalse();
    }

    @Test
    @DisplayName("findAllByStatus - Returns only applications matching given status")
    void findAllByStatus_returnsMatchingStatusApplications() {
        User user1 = createAndPersistUser("user1_status@example.com", false);
        User user2 = createAndPersistUser("user2_status@example.com", false);
        User admin = createAndPersistUser("admin_status@example.com", true);

        SellerApplication pendingApp = new SellerApplication(user1, "Pending Shop", "Notes");
        SellerApplication approvedApp = new SellerApplication(user2, "Approved Shop", "Notes");
        approvedApp.approve(admin);

        sellerApplicationRepository.saveAndFlush(pendingApp);
        sellerApplicationRepository.saveAndFlush(approvedApp);
        entityManager.clear();

        List<SellerApplication> pendingList = sellerApplicationRepository.findAllByStatus(ApplicationStatus.PENDING);
        List<SellerApplication> approvedList = sellerApplicationRepository.findAllByStatus(ApplicationStatus.APPROVED);
        List<SellerApplication> rejectedList = sellerApplicationRepository.findAllByStatus(ApplicationStatus.REJECTED);

        assertThat(pendingList).hasSize(1);
        assertThat(pendingList.getFirst().getBusinessName()).isEqualTo("Pending Shop");

        assertThat(approvedList).hasSize(1);
        assertThat(approvedList.getFirst().getBusinessName()).isEqualTo("Approved Shop");

        assertThat(rejectedList).isEmpty();
    }

    @Test
    @DisplayName("findFirstByUserIdOrderByCreatedAtDesc - Returns the latest application when user has multiple")
    void findFirstByUserIdOrderByCreatedAtDesc_returnsLatestApplication() {
        User user = createAndPersistUser("latest_applicant@example.com", false);
        User admin = createAndPersistUser("admin_latest@example.com", true);

        SellerApplication pastApp = new SellerApplication(user, "Past Shop", "First application");
        pastApp.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        pastApp.reject(admin);
        sellerApplicationRepository.saveAndFlush(pastApp);

        SellerApplication recentApp = new SellerApplication(user, "Recent Shop", "Second application");
        recentApp.setCreatedAt(Instant.now());
        sellerApplicationRepository.saveAndFlush(recentApp);
        entityManager.clear();

        Optional<SellerApplication> latestOpt = sellerApplicationRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        assertThat(latestOpt).isPresent();
        assertThat(latestOpt.get().getId()).isEqualTo(recentApp.getId());
        assertThat(latestOpt.get().getBusinessName()).isEqualTo("Recent Shop");
        assertThat(latestOpt.get().getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("findFirstByUserIdOrderByCreatedAtDesc - Returns empty when user has no applications")
    void findFirstByUserIdOrderByCreatedAtDesc_whenNoApplication_returnsEmpty() {
        User user = createAndPersistUser("empty_applicant@example.com", false);

        Optional<SellerApplication> latestOpt = sellerApplicationRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        assertThat(latestOpt).isEmpty();
    }
}
