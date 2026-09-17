package com.omar.vendora.sellers.controller;

import com.jayway.jsonpath.JsonPath;
import com.omar.vendora.identity.dto.LoginRequest;
import com.omar.vendora.identity.repository.RefreshTokenRepository;
import com.omar.vendora.security.JwtService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSellerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String RAW_PASSWORD = "Password123!";

    @BeforeEach
    void setUp() {
        sellerProfileRepository.deleteAll();
        sellerApplicationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createAndPersistUser(String email, String fullName, boolean isCustomer, boolean isSeller, boolean isAdmin) {
        User user = new User(email, passwordEncoder.encode(RAW_PASSWORD), fullName, "+1234567890");
        user.setStatus(UserStatus.ACTIVE);
        user.setCustomer(isCustomer);
        user.setSeller(isSeller);
        user.setAdmin(isAdmin);
        return userRepository.save(user);
    }

    private SellerProfile createAndPersistSellerProfile(User user, String displayName, SellerProfileStatus status) {
        SellerProfile profile = new SellerProfile(user, displayName);
        profile.setStatus(status);
        return sellerProfileRepository.save(profile);
    }

    // =========================================================================
    // 1. Admin can suspend seller
    // =========================================================================

    @Test
    @DisplayName("POST /admin/sellers/{id}/suspend - Admin successfully suspends active seller")
    void suspendSeller_asAdmin_success() throws Exception {
        User admin = createAndPersistUser("admin@example.com", "Admin User", false, false, true);
        User sellerUser = createAndPersistUser("seller@example.com", "Seller User", true, true, false);
        SellerProfile profile = createAndPersistSellerProfile(sellerUser, "Electro Shop", SellerProfileStatus.ACTIVE);

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(post("/admin/sellers/{id}/suspend", profile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(profile.getId().toString()))
            .andExpect(jsonPath("$.userId").value(sellerUser.getId().toString()))
            .andExpect(jsonPath("$.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.displayName").value("Electro Shop"));

        // Verify DB updates
        SellerProfile updatedProfile = sellerProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(updatedProfile.getStatus()).isEqualTo(SellerProfileStatus.SUSPENDED);

        User updatedUser = userRepository.findById(sellerUser.getId()).orElseThrow();
        assertThat(updatedUser.isSeller()).isFalse();
        assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    // =========================================================================
    // 2. Admin can reactivate seller
    // =========================================================================

    @Test
    @DisplayName("POST /admin/sellers/{id}/reactivate - Admin successfully reactivates suspended seller")
    void reactivateSeller_asAdmin_success() throws Exception {
        User admin = createAndPersistUser("admin@example.com", "Admin User", false, false, true);
        User sellerUser = createAndPersistUser("seller@example.com", "Seller User", true, false, false);
        SellerProfile profile = createAndPersistSellerProfile(sellerUser, "Electro Shop", SellerProfileStatus.SUSPENDED);

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(post("/admin/sellers/{id}/reactivate", profile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(profile.getId().toString()))
            .andExpect(jsonPath("$.userId").value(sellerUser.getId().toString()))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.displayName").value("Electro Shop"));

        // Verify DB updates
        SellerProfile updatedProfile = sellerProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(updatedProfile.getStatus()).isEqualTo(SellerProfileStatus.ACTIVE);

        User updatedUser = userRepository.findById(sellerUser.getId()).orElseThrow();
        assertThat(updatedUser.isSeller()).isTrue();
        assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    // =========================================================================
    // 3. Suspended seller cannot perform documented seller actions
    // =========================================================================

    @Test
    @DisplayName("Suspended seller cannot perform documented seller actions")
    void suspendedSeller_cannotPerformSellerActions() throws Exception {
        User admin = createAndPersistUser("admin@example.com", "Admin User", false, false, true);
        User sellerUser = createAndPersistUser("seller@example.com", "Seller User", true, true, false);
        SellerProfile profile = createAndPersistSellerProfile(sellerUser, "Electro Shop", SellerProfileStatus.ACTIVE);

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        // Admin suspends seller
        mockMvc.perform(post("/admin/sellers/{id}/suspend", profile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk());

        // Seller logs in after suspension
        LoginRequest loginRequest = new LoginRequest("seller@example.com", RAW_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

        // Attempt documented seller-protected endpoint
        mockMvc.perform(get("/api/test/rbac/seller")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // =========================================================================
    // 4. Reactivated seller can perform documented seller actions
    // =========================================================================

    @Test
    @DisplayName("Reactivated seller can perform documented seller actions")
    void reactivatedSeller_canPerformSellerActions() throws Exception {
        User admin = createAndPersistUser("admin@example.com", "Admin User", false, false, true);
        User sellerUser = createAndPersistUser("seller@example.com", "Seller User", true, false, false);
        SellerProfile profile = createAndPersistSellerProfile(sellerUser, "Electro Shop", SellerProfileStatus.SUSPENDED);

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        // Admin reactivates seller
        mockMvc.perform(post("/admin/sellers/{id}/reactivate", profile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk());

        // Seller logs in after reactivation
        LoginRequest loginRequest = new LoginRequest("seller@example.com", RAW_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

        // Attempt documented seller-protected endpoint
        mockMvc.perform(get("/api/test/rbac/seller")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Seller access granted"));
    }

    // =========================================================================
    // 5. User.status remains unchanged
    // =========================================================================

    @Test
    @DisplayName("User.status remains ACTIVE throughout suspension and reactivation")
    void userStatus_remainsUnchangedAcrossTransitions() throws Exception {
        User admin = createAndPersistUser("admin@example.com", "Admin User", false, false, true);
        User sellerUser = createAndPersistUser("seller@example.com", "Seller User", true, true, false);
        SellerProfile profile = createAndPersistSellerProfile(sellerUser, "Electro Shop", SellerProfileStatus.ACTIVE);

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        // Initial check
        assertThat(userRepository.findById(sellerUser.getId()).orElseThrow().getStatus())
            .isEqualTo(UserStatus.ACTIVE);

        // Suspend
        mockMvc.perform(post("/admin/sellers/{id}/suspend", profile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk());

        // Check after suspension
        User afterSuspensionUser = userRepository.findById(sellerUser.getId()).orElseThrow();
        assertThat(afterSuspensionUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(afterSuspensionUser.isSeller()).isFalse();

        // Reactivate
        mockMvc.perform(post("/admin/sellers/{id}/reactivate", profile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk());

        // Check after reactivation
        User afterReactivationUser = userRepository.findById(sellerUser.getId()).orElseThrow();
        assertThat(afterReactivationUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(afterReactivationUser.isSeller()).isTrue();
    }

    // =========================================================================
    // 6. Invalid state transitions are rejected
    // =========================================================================

    @Test
    @DisplayName("POST /admin/sellers/{id}/suspend - Suspending an already suspended seller returns 409 INVALID_STATE_TRANSITION")
    void suspendSeller_alreadySuspended_returns409() throws Exception {
        User admin = createAndPersistUser("admin@example.com", "Admin User", false, false, true);
        User sellerUser = createAndPersistUser("seller@example.com", "Seller User", true, false, false);
        SellerProfile profile = createAndPersistSellerProfile(sellerUser, "Electro Shop", SellerProfileStatus.SUSPENDED);

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(post("/admin/sellers/{id}/suspend", profile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"))
            .andExpect(jsonPath("$.error.message").value("Seller profile is already suspended"));
    }

    @Test
    @DisplayName("POST /admin/sellers/{id}/reactivate - Reactivating an already active seller returns 409 INVALID_STATE_TRANSITION")
    void reactivateSeller_alreadyActive_returns409() throws Exception {
        User admin = createAndPersistUser("admin@example.com", "Admin User", false, false, true);
        User sellerUser = createAndPersistUser("seller@example.com", "Seller User", true, true, false);
        SellerProfile profile = createAndPersistSellerProfile(sellerUser, "Electro Shop", SellerProfileStatus.ACTIVE);

        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(post("/admin/sellers/{id}/reactivate", profile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"))
            .andExpect(jsonPath("$.error.message").value("Seller profile is already active"));
    }

    // =========================================================================
    // 7. Non-admin cannot suspend or reactivate
    // =========================================================================

    @Test
    @DisplayName("POST /admin/sellers/{id}/suspend and reactivate - Unauthenticated request returns 401 UNAUTHORIZED")
    void nonAdmin_unauthenticated_returns401() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(post("/admin/sellers/{id}/suspend", randomId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/admin/sellers/{id}/reactivate", randomId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /admin/sellers/{id}/suspend and reactivate - Regular customer or seller returns 403 FORBIDDEN")
    void nonAdmin_authenticatedAsCustomerOrSeller_returns403() throws Exception {
        User regularUser = createAndPersistUser("customer@example.com", "Customer User", true, false, false);
        User sellerUser = createAndPersistUser("seller@example.com", "Seller User", true, true, false);
        SellerProfile profile = createAndPersistSellerProfile(sellerUser, "Electro Shop", SellerProfileStatus.ACTIVE);

        String customerToken = jwtService.generateToken(regularUser.getId(), regularUser.getEmail(), List.of("ROLE_CUSTOMER"));
        String sellerToken = jwtService.generateToken(sellerUser.getId(), sellerUser.getEmail(), List.of("ROLE_CUSTOMER", "ROLE_SELLER"));

        // Customer attempts suspend
        mockMvc.perform(post("/admin/sellers/{id}/suspend", profile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Customer attempts reactivate
        mockMvc.perform(post("/admin/sellers/{id}/reactivate", profile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Seller attempts suspend
        mockMvc.perform(post("/admin/sellers/{id}/suspend", profile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Seller attempts reactivate
        mockMvc.perform(post("/admin/sellers/{id}/reactivate", profile.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // =========================================================================
    // 8. Seller not found returns 404
    // =========================================================================

    @Test
    @DisplayName("POST /admin/sellers/{id}/suspend and reactivate - Non-existent seller profile returns 404 SELLER_NOT_FOUND")
    void sellerNotFound_returns404() throws Exception {
        User admin = createAndPersistUser("admin@example.com", "Admin User", false, false, true);
        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(post("/admin/sellers/{id}/suspend", nonExistentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("SELLER_NOT_FOUND"));

        mockMvc.perform(post("/admin/sellers/{id}/reactivate", nonExistentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("SELLER_NOT_FOUND"));
    }

    // =========================================================================
    // 9. Historical records untouched (LEARN_BY_DOING.md line 498)
    // =========================================================================

    @Test
    @DisplayName("Historical records (Orders, Payouts) remain untouched by suspension/reactivation (TODO placeholder test)")
    void historicalRecordsUntouched_placeholder() {
        // TODO: When Order and Payout modules are implemented in subsequent stages,
        // verify that suspending or reactivating a SellerProfile leaves historical
        // CustomerOrder, SellerOrder, and Payout records unmodified.
        assertThat(true).isTrue();
    }
}
