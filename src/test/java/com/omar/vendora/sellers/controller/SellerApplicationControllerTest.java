package com.omar.vendora.sellers.controller;

import com.omar.vendora.identity.repository.RefreshTokenRepository;
import com.omar.vendora.security.JwtService;
import com.omar.vendora.sellers.domain.ApplicationStatus;
import com.omar.vendora.sellers.domain.SellerApplication;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SellerApplicationControllerTest {

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

    // =========================================================================
    // POST /seller-applications Happy Path Tests
    // =========================================================================

    @Test
    @DisplayName("POST /seller-applications - Happy path with businessName and notes returns 201 Created and status PENDING")
    void submitApplication_happyPath_withNotes() throws Exception {
        User user = createAndPersistUser("customer1@example.com", "Customer One", "+1234567890", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                \"businessName\": \"Acme Gadgets\",
                \"notes\": \"Selling electronic components and accessories\"
            }
            """;

        mockMvc.perform(post("/seller-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.userId").value(user.getId().toString()))
            .andExpect(jsonPath("$.businessName").value("Acme Gadgets"))
            .andExpect(jsonPath("$.notes").value("Selling electronic components and accessories"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.decidedAt").doesNotExist())
            .andExpect(jsonPath("$.decidedByAdminId").doesNotExist());
    }

    @Test
    @DisplayName("POST /seller-applications - Happy path with null notes returns 201 Created and status PENDING")
    void submitApplication_happyPath_withoutNotes() throws Exception {
        User user = createAndPersistUser("customer2@example.com", "Customer Two", "+1234567891", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                \"businessName\": \"Minimalist Store\"
            }
            """;

        mockMvc.perform(post("/seller-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.userId").value(user.getId().toString()))
            .andExpect(jsonPath("$.businessName").value("Minimalist Store"))
            .andExpect(jsonPath("$.notes").doesNotExist())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /seller-applications - Snake_case alias business_name is accepted")
    void submitApplication_snakeCaseAlias() throws Exception {
        User user = createAndPersistUser("customer_snake@example.com", "Snake Customer", "+1234567892", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                \"business_name\": \"Snake Case Goods\",
                \"notes\": \"Testing alias\"
            }
            """;

        mockMvc.perform(post("/seller-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.businessName").value("Snake Case Goods"));
    }

    // =========================================================================
    // POST /seller-applications Validation Error Tests
    // =========================================================================

    @Test
    @DisplayName("POST /seller-applications - Rejects missing businessName with 400 Bad Request")
    void submitApplication_missingBusinessName_returns400() throws Exception {
        User user = createAndPersistUser("customer3@example.com", "Customer Three", "+1234567893", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                \"notes\": \"Missing business name\"
            }
            """;

        mockMvc.perform(post("/seller-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[0].field").value("businessName"));
    }

    @Test
    @DisplayName("POST /seller-applications - Rejects blank businessName with 400 Bad Request")
    void submitApplication_blankBusinessName_returns400() throws Exception {
        User user = createAndPersistUser("customer4@example.com", "Customer Four", "+1234567894", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                \"businessName\": \"   \"
            }
            """;

        mockMvc.perform(post("/seller-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[0].field").value("businessName"));
    }

    @Test
    @DisplayName("POST /seller-applications - Rejects businessName exceeding 255 chars with 400 Bad Request")
    void submitApplication_businessNameTooLong_returns400() throws Exception {
        User user = createAndPersistUser("customer5@example.com", "Customer Five", "+1234567895", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String longName = "A".repeat(256);
        String requestBody = """
            {
                \"businessName\": \"%s\"
            }
            """.formatted(longName);

        mockMvc.perform(post("/seller-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[0].field").value("businessName"));
    }

    // =========================================================================
    // POST /seller-applications Business Rule / Conflict Tests
    // =========================================================================

    @Test
    @DisplayName("POST /seller-applications - Rejects duplicate pending application with 409 Conflict")
    void submitApplication_duplicatePending_returns409() throws Exception {
        User user = createAndPersistUser("customer6@example.com", "Customer Six", "+1234567896", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody1 = """
            {
                \"businessName\": \"First Application\",
                \"notes\": \"First attempt\"
            }
            """;

        mockMvc.perform(post("/seller-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody1))
            .andExpect(status().isCreated());

        String requestBody2 = """
            {
                \"businessName\": \"Second Application\",
                \"notes\": \"Second attempt\"
            }
            """;

        mockMvc.perform(post("/seller-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody2))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("SELLER_APPLICATION_ALREADY_PENDING"));
    }

    @Test
    @DisplayName("POST /seller-applications - Allows resubmission if previous application was rejected")
    void submitApplication_resubmissionAfterRejected_returns201() throws Exception {
        User user = createAndPersistUser("customer7@example.com", "Customer Seven", "+1234567897", true, false, false);
        User admin = createAndPersistUser("admin1@example.com", "Admin One", "+1234567898", false, false, true);

        SellerApplication oldApp = new SellerApplication(user, "Old Business", "Previous notes");
        oldApp.reject(admin);
        sellerApplicationRepository.save(oldApp);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                \"businessName\": \"New Improved Business\",
                \"notes\": \"Fixed earlier issues\"
            }
            """;

        mockMvc.perform(post("/seller-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.businessName").value("New Improved Business"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // =========================================================================
    // POST /seller-applications Concurrency / DB Constraint Race Test
    // =========================================================================

    @Test
    @DisplayName("POST /seller-applications - Concurrent submissions only one succeeds, other gets 409")
    void submitApplication_concurrentSubmissions_onlyOneSucceeds() throws Exception {
        User user = createAndPersistUser("customer_race@example.com", "Race Customer", "+1234567899", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                \"businessName\": \"Concurrent Fast Store\",
                \"notes\": \"Racing request\"
            }
            """;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Integer> submitTask = () -> {
            try {
                return mockMvc.perform(post("/seller-applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                    .andReturn().getResponse().getStatus();
            } catch (Exception e) {
                return 500;
            }
        };

        List<Future<Integer>> futures = executor.invokeAll(List.of(submitTask, submitTask));
        executor.shutdown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }

    // =========================================================================
    // POST /seller-applications Auth & Security Tests
    // =========================================================================

    @Test
    @DisplayName("POST /seller-applications - Unauthenticated request returns 401 Unauthorized")
    void submitApplication_unauthenticated_returns401() throws Exception {
        String requestBody = """
            {
                \"businessName\": \"Anon Store\"
            }
            """;

        mockMvc.perform(post("/seller-applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /seller-applications - Admin-only user without CUSTOMER role returns 403 Forbidden")
    void submitApplication_adminOnly_returns403() throws Exception {
        User admin = createAndPersistUser("admin_only@example.com", "Admin Only", "+1234567800", false, false, true);
        String token = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        String requestBody = """
            {
                \"businessName\": \"Admin Store\"
            }
            """;

        mockMvc.perform(post("/seller-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /seller-applications - IDOR Protection: authenticated user identity cannot be spoofed")
    void submitApplication_idorProtection_usesTokenIdentity() throws Exception {
        User realUser = createAndPersistUser("real_user@example.com", "Real User", "+1234567801", true, false, false);
        User victimUser = createAndPersistUser("victim_user@example.com", "Victim User", "+1234567802", true, false, false);

        String realUserToken = jwtService.generateToken(realUser.getId(), realUser.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                \"businessName\": \"Spoof Attempt Store\"
            }
            """;

        mockMvc.perform(post("/seller-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + realUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(realUser.getId().toString()));

        assertThat(sellerApplicationRepository.existsByUserIdAndStatus(victimUser.getId(), ApplicationStatus.PENDING)).isFalse();
        assertThat(sellerApplicationRepository.existsByUserIdAndStatus(realUser.getId(), ApplicationStatus.PENDING)).isTrue();
    }

    // =========================================================================
    // GET /seller-applications/me Happy Path & Security Tests
    // =========================================================================

    @Test
    @DisplayName("GET /seller-applications/me - Happy path returns current user's latest application")
    void getMyApplication_happyPath_returnsLatest() throws Exception {
        User user = createAndPersistUser("customer_me@example.com", "Customer Me", "+1234567803", true, false, false);
        User admin = createAndPersistUser("admin_decider@example.com", "Admin Decider", "+1234567804", false, false, true);

        SellerApplication oldRejected = new SellerApplication(user, "Old Rejected Store", "Bad notes");
        oldRejected.reject(admin);
        sellerApplicationRepository.save(oldRejected);

        SellerApplication latestPending = new SellerApplication(user, "Current Store", "Good notes");
        sellerApplicationRepository.save(latestPending);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        mockMvc.perform(get("/seller-applications/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(latestPending.getId().toString()))
            .andExpect(jsonPath("$.userId").value(user.getId().toString()))
            .andExpect(jsonPath("$.businessName").value("Current Store"))
            .andExpect(jsonPath("$.notes").value("Good notes"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /seller-applications/me - Returns 404 when user has never submitted an application")
    void getMyApplication_noApplication_returns404() throws Exception {
        User user = createAndPersistUser("customer_empty@example.com", "Customer Empty", "+1234567805", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        mockMvc.perform(get("/seller-applications/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("SELLER_APPLICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /seller-applications/me - Unauthenticated request returns 401")
    void getMyApplication_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/seller-applications/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /seller-applications/me - Non-customer token returns 403 Forbidden")
    void getMyApplication_nonCustomer_returns403() throws Exception {
        User admin = createAndPersistUser("admin_only2@example.com", "Admin Two", "+1234567806", false, false, true);
        String token = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/seller-applications/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
