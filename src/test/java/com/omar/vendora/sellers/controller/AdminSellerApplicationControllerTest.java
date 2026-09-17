package com.omar.vendora.sellers.controller;

import com.omar.vendora.identity.repository.RefreshTokenRepository;
import com.omar.vendora.security.JwtService;
import com.omar.vendora.sellers.domain.ApplicationStatus;
import com.omar.vendora.sellers.domain.SellerApplication;
import com.omar.vendora.sellers.domain.SellerProfile;
import com.omar.vendora.sellers.domain.SellerProfileStatus;
import com.omar.vendora.sellers.repository.SellerApplicationRepository;
import com.omar.vendora.sellers.repository.SellerProfileRepository;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.domain.UserStatus;
import com.omar.vendora.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSellerApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        sellerProfileRepository.deleteAll();
        sellerApplicationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createAndPersistUser(String email, String fullName, String phone, boolean isCustomer, boolean isSeller, boolean isAdmin) {
        User user = new User(email, "hashed_pw_secret_123", fullName, phone);
        user.setStatus(UserStatus.ACTIVE);
        user.setCustomer(isCustomer);
        user.setSeller(isSeller);
        user.setAdmin(isAdmin);
        return userRepository.save(user);
    }

    private SellerApplication createAndPersistPendingApplication(User user, String businessName) {
        SellerApplication app = new SellerApplication(user, businessName, "Application notes for " + businessName);
        return sellerApplicationRepository.save(app);
    }

    // =========================================================================
    // 1. Admin Approves Pending Application (Tests 1, 2, 3)
    // =========================================================================

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/approve - Admin approves pending application, creates SellerProfile, updates User, returns 200")
    void approveApplication_success() throws Exception {
        User applicant = createAndPersistUser("applicant1@example.com", "Applicant One", "+1234567890", true, false, false);
        User admin = createAndPersistUser("admin1@example.com", "Admin One", "+1234567891", false, false, true);
        SellerApplication app = createAndPersistPendingApplication(applicant, "Electro World");

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(post("/admin/seller-applications/{id}/approve", app.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(app.getId().toString()))
            .andExpect(jsonPath("$.userId").value(applicant.getId().toString()))
            .andExpect(jsonPath("$.businessName").value("Electro World"))
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.decidedByAdminId").value(admin.getId().toString()))
            .andExpect(jsonPath("$.decidedAt").isNotEmpty());

        // 1. Verify application state in DB
        SellerApplication updatedApp = sellerApplicationRepository.findById(app.getId()).orElseThrow();
        assertThat(updatedApp.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(updatedApp.getDecidedByAdminId()).isEqualTo(admin.getId());
        assertThat(updatedApp.getDecidedAt()).isNotNull();

        // 2. Verify applicant gains seller capability
        User updatedApplicant = userRepository.findById(applicant.getId()).orElseThrow();
        assertThat(updatedApplicant.isSeller()).isTrue();

        // 3. Verify SellerProfile is created in DB
        Optional<SellerProfile> profileOpt = sellerProfileRepository.findByUserId(applicant.getId());
        assertThat(profileOpt).isPresent();
        SellerProfile profile = profileOpt.get();
        assertThat(profile.getUser().getId()).isEqualTo(applicant.getId());
        assertThat(profile.getDisplayName()).isEqualTo("Electro World");
        assertThat(profile.getStatus()).isEqualTo(SellerProfileStatus.ACTIVE);
        assertThat(profile.getApprovedAt()).isNotNull();
    }

    // =========================================================================
    // 2. Admin Rejects Pending Application (Tests 4, 5)
    // =========================================================================

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/reject - Admin rejects pending application with reason, returns 200")
    void rejectApplication_withReason_success() throws Exception {
        User applicant = createAndPersistUser("applicant2@example.com", "Applicant Two", "+1234567892", true, false, false);
        User admin = createAndPersistUser("admin2@example.com", "Admin Two", "+1234567893", false, false, true);
        SellerApplication app = createAndPersistPendingApplication(applicant, "Suspicious Goods");

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        String requestBody = """
            {
                \"reason\": \"Invalid business documentation provided\"
            }
            """;

        mockMvc.perform(post("/admin/seller-applications/{id}/reject", app.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(app.getId().toString()))
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.decidedByAdminId").value(admin.getId().toString()))
            .andExpect(jsonPath("$.decidedAt").isNotEmpty());

        // Verify application state in DB
        SellerApplication updatedApp = sellerApplicationRepository.findById(app.getId()).orElseThrow();
        assertThat(updatedApp.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(updatedApp.getDecidedByAdminId()).isEqualTo(admin.getId());
        assertThat(updatedApp.getDecidedAt()).isNotNull();

        // Verify applicant does NOT gain seller capability
        User updatedApplicant = userRepository.findById(applicant.getId()).orElseThrow();
        assertThat(updatedApplicant.isSeller()).isFalse();

        // Verify SellerProfile is NOT created
        assertThat(sellerProfileRepository.findByUserId(applicant.getId())).isEmpty();
    }

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/reject - Admin rejects without body, returns 200")
    void rejectApplication_withoutBody_success() throws Exception {
        User applicant = createAndPersistUser("applicant3@example.com", "Applicant Three", "+1234567894", true, false, false);
        User admin = createAndPersistUser("admin3@example.com", "Admin Three", "+1234567895", false, false, true);
        SellerApplication app = createAndPersistPendingApplication(applicant, "Another Store");

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(post("/admin/seller-applications/{id}/reject", app.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(app.getId().toString()))
            .andExpect(jsonPath("$.status").value("REJECTED"));

        SellerApplication updatedApp = sellerApplicationRepository.findById(app.getId()).orElseThrow();
        assertThat(updatedApp.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(sellerProfileRepository.findByUserId(applicant.getId())).isEmpty();
    }

    // =========================================================================
    // 3. Double-Decision Rejection / Guard Against Already Decided (Tests 6, 7)
    // =========================================================================

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/approve - Rejects approving an already APPROVED application with 409 Conflict")
    void approveApplication_alreadyApproved_returns409() throws Exception {
        User applicant = createAndPersistUser("applicant4@example.com", "Applicant Four", "+1234567896", true, false, false);
        User admin = createAndPersistUser("admin4@example.com", "Admin Four", "+1234567897", false, false, true);
        SellerApplication app = createAndPersistPendingApplication(applicant, "Store Four");

        app.approve(admin);
        sellerApplicationRepository.save(app);

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(post("/admin/seller-applications/{id}/approve", app.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("SELLER_APPLICATION_ALREADY_DECIDED"));
    }

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/reject - Rejects rejecting an already APPROVED application with 409 Conflict")
    void rejectApplication_alreadyApproved_returns409() throws Exception {
        User applicant = createAndPersistUser("applicant5@example.com", "Applicant Five", "+1234567898", true, false, false);
        User admin = createAndPersistUser("admin5@example.com", "Admin Five", "+1234567899", false, false, true);
        SellerApplication app = createAndPersistPendingApplication(applicant, "Store Five");

        app.approve(admin);
        sellerApplicationRepository.save(app);

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(post("/admin/seller-applications/{id}/reject", app.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("SELLER_APPLICATION_ALREADY_DECIDED"));
    }

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/approve - Rejects approving an already REJECTED application with 409 Conflict")
    void approveApplication_alreadyRejected_returns409() throws Exception {
        User applicant = createAndPersistUser("applicant6@example.com", "Applicant Six", "+1234567810", true, false, false);
        User admin = createAndPersistUser("admin6@example.com", "Admin Six", "+1234567811", false, false, true);
        SellerApplication app = createAndPersistPendingApplication(applicant, "Store Six");

        app.reject(admin);
        sellerApplicationRepository.save(app);

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(post("/admin/seller-applications/{id}/approve", app.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("SELLER_APPLICATION_ALREADY_DECIDED"));
    }

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/reject - Rejects rejecting an already REJECTED application with 409 Conflict")
    void rejectApplication_alreadyRejected_returns409() throws Exception {
        User applicant = createAndPersistUser("applicant7@example.com", "Applicant Seven", "+1234567812", true, false, false);
        User admin = createAndPersistUser("admin7@example.com", "Admin Seven", "+1234567813", false, false, true);
        SellerApplication app = createAndPersistPendingApplication(applicant, "Store Seven");

        app.reject(admin);
        sellerApplicationRepository.save(app);

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(post("/admin/seller-applications/{id}/reject", app.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("SELLER_APPLICATION_ALREADY_DECIDED"));
    }

    // =========================================================================
    // 4. Authorization & Security Tests (Test 8)
    // =========================================================================

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/approve - Non-admin user (customer) returns 403 Forbidden")
    void approveApplication_customerRole_returns403() throws Exception {
        User customer = createAndPersistUser("customer@example.com", "Customer", "+1234567814", true, false, false);
        SellerApplication app = createAndPersistPendingApplication(customer, "Customer Store");

        String customerToken = jwtService.generateToken(customer.getId(), customer.getEmail(), List.of("ROLE_CUSTOMER"));

        mockMvc.perform(post("/admin/seller-applications/{id}/approve", app.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/reject - Non-admin user (seller) returns 403 Forbidden")
    void rejectApplication_sellerRole_returns403() throws Exception {
        User seller = createAndPersistUser("seller@example.com", "Seller", "+1234567815", false, true, false);
        SellerApplication app = createAndPersistPendingApplication(seller, "Seller Store");

        String sellerToken = jwtService.generateToken(seller.getId(), seller.getEmail(), List.of("ROLE_SELLER"));

        mockMvc.perform(post("/admin/seller-applications/{id}/reject", app.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/approve - Unauthenticated returns 401 Unauthorized")
    void approveApplication_unauthenticated_returns401() throws Exception {
        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(post("/admin/seller-applications/{id}/approve", fakeId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/reject - Unauthenticated returns 401 Unauthorized")
    void rejectApplication_unauthenticated_returns401() throws Exception {
        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(post("/admin/seller-applications/{id}/reject", fakeId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // =========================================================================
    // 5. Not Found Tests
    // =========================================================================

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/approve - Non-existent ID returns 404 Not Found")
    void approveApplication_notFound_returns404() throws Exception {
        User admin = createAndPersistUser("admin_nf1@example.com", "Admin NF", "+1234567816", false, false, true);
        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(post("/admin/seller-applications/{id}/approve", fakeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("SELLER_APPLICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/reject - Non-existent ID returns 404 Not Found")
    void rejectApplication_notFound_returns404() throws Exception {
        User admin = createAndPersistUser("admin_nf2@example.com", "Admin NF", "+1234567817", false, false, true);
        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(post("/admin/seller-applications/{id}/reject", fakeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("SELLER_APPLICATION_NOT_FOUND"));
    }

    // =========================================================================
    // 6. Transactional Behavior Test (Test 9)
    // =========================================================================

    @Test
    @DisplayName("POST /admin/seller-applications/{id}/approve - Transaction rollback: if duplicate profile fails, application and user changes roll back")
    void approveApplication_profileSaveFailure_rollsBackTransaction() throws Exception {
        User applicant = createAndPersistUser("applicant_tx@example.com", "Applicant Tx", "+1234567818", true, false, false);
        User admin = createAndPersistUser("admin_tx@example.com", "Admin Tx", "+1234567819", false, false, true);
        SellerApplication app = createAndPersistPendingApplication(applicant, "Duplicate Profile Store");

        // Pre-create a profile to trigger unique constraint violation (user_id unique)
        SellerProfile existingProfile = new SellerProfile(applicant, "Existing Profile");
        sellerProfileRepository.save(existingProfile);

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        // Attempting to approve must fail due to unique constraint on seller_profiles.user_id
        mockMvc.perform(post("/admin/seller-applications/{id}/approve", app.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict());

        // Verify transaction rolled back: application status must remain PENDING
        SellerApplication appAfter = sellerApplicationRepository.findById(app.getId()).orElseThrow();
        assertThat(appAfter.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(appAfter.getDecidedAt()).isNull();
        assertThat(appAfter.getDecidedByAdminId()).isNull();

        // Verify user.isSeller remained false
        User userAfter = userRepository.findById(applicant.getId()).orElseThrow();
        assertThat(userAfter.isSeller()).isFalse();
    }
}
