package com.omar.vendora.users.controller;

import com.omar.vendora.identity.repository.RefreshTokenRepository;
import com.omar.vendora.security.JwtService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
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

    @Test
    @DisplayName("GET /users/me - Happy path: authenticated customer receives own profile (200 OK)")
    void getCurrentUser_happyPath_returns200AndProfile() throws Exception {
        User user = createAndPersistUser("customer@example.com", "Customer One", "+1234567890", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        mockMvc.perform(get("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(user.getId().toString()))
            .andExpect(jsonPath("$.email").value("customer@example.com"))
            .andExpect(jsonPath("$.fullName").value("Customer One"))
            .andExpect(jsonPath("$.phone").value("+1234567890"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.isCustomer").value(true))
            .andExpect(jsonPath("$.isSeller").value(false))
            .andExpect(jsonPath("$.isAdmin").value(false))
            .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET /users/me - Identity isolation: returns only authenticated user's profile, not another user")
    void getCurrentUser_identityIsolation_returnsAuthenticatedUserOnly() throws Exception {
        User userA = createAndPersistUser("userA@example.com", "User Alpha", "+1111111111", true, false, false);
        User userB = createAndPersistUser("userB@example.com", "User Beta", "+2222222222", true, true, false);

        String tokenA = jwtService.generateToken(userA.getId(), userA.getEmail(), List.of("ROLE_CUSTOMER"));
        String tokenB = jwtService.generateToken(userB.getId(), userB.getEmail(), List.of("ROLE_CUSTOMER", "ROLE_SELLER"));

        // User A calls /users/me -> gets User A
        mockMvc.perform(get("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(userA.getId().toString()))
            .andExpect(jsonPath("$.email").value("userA@example.com"))
            .andExpect(jsonPath("$.fullName").value("User Alpha"));

        // User B calls /users/me -> gets User B
        mockMvc.perform(get("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(userB.getId().toString()))
            .andExpect(jsonPath("$.email").value("userB@example.com"))
            .andExpect(jsonPath("$.fullName").value("User Beta"));

        // User A attempts to pass ?userId=userB.id -> parameter is ignored, returns User A
        mockMvc.perform(get("/users/me")
                .param("userId", userB.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(userA.getId().toString()))
            .andExpect(jsonPath("$.email").value("userA@example.com"))
            .andExpect(jsonPath("$.fullName").value("User Alpha"));
    }

    @Test
    @DisplayName("GET /users/me - Sensitive data: does not expose password, passwordHash, or credentials")
    void getCurrentUser_sensitiveDataNotExposed() throws Exception {
        User user = createAndPersistUser("secret@example.com", "Secret User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        mockMvc.perform(get("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.password_hash").doesNotExist())
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    @DisplayName("GET /users/me - Unauthenticated request returns 401 UNAUTHORIZED")
    void getCurrentUser_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Authentication is required to access this resource"));
    }

    @Test
    @DisplayName("GET /users/me - Tampered JWT returns 401 UNAUTHORIZED")
    void getCurrentUser_tamperedJwt_returns401() throws Exception {
        User user = createAndPersistUser("tamper@example.com", "Tamper User", null, true, false, false);
        String validToken = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "abcde";

        mockMvc.perform(get("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /users/me - Malformed token returns 401 UNAUTHORIZED")
    void getCurrentUser_malformedToken_returns401() throws Exception {
        mockMvc.perform(get("/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-not-a-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /users/me - Role versatility: SELLER and ADMIN can also retrieve own profile (Role: Any)")
    void getCurrentUser_sellerAndAdminRoles_returns200() throws Exception {
        User seller = createAndPersistUser("seller@example.com", "Seller User", null, true, true, false);
        String sellerToken = jwtService.generateToken(seller.getId(), seller.getEmail(), List.of("ROLE_SELLER"));

        mockMvc.perform(get("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(seller.getId().toString()))
            .andExpect(jsonPath("$.isSeller").value(true));

        User admin = createAndPersistUser("admin@example.com", "Admin User", null, true, false, true);
        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(admin.getId().toString()))
            .andExpect(jsonPath("$.isAdmin").value(true));
    }

    @Test
    @DisplayName("GET /users/me - Non-existent user ID in valid token returns 404 USER_NOT_FOUND")
    void getCurrentUser_userNotFound_returns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        String token = jwtService.generateToken(nonExistentId, "deleted@example.com", List.of("ROLE_CUSTOMER"));

        mockMvc.perform(get("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
            .andExpect(jsonPath("$.error.message").value("User with id '" + nonExistentId + "' not found"));
    }

    // ==========================================
    // PATCH /users/me Tests
    // ==========================================

    @Test
    @DisplayName("PATCH /users/me - Happy path: update fullName only, phone remains unchanged")
    void patchCurrentUser_happyPath_updatesFullNameOnly() throws Exception {
        User user = createAndPersistUser("patch1@example.com", "Original Name", "+1234567890", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "fullName": "Updated Name Only"
            }
            """;

        mockMvc.perform(patch("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(user.getId().toString()))
            .andExpect(jsonPath("$.email").value("patch1@example.com"))
            .andExpect(jsonPath("$.fullName").value("Updated Name Only"))
            .andExpect(jsonPath("$.phone").value("+1234567890"));

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getFullName()).isEqualTo("Updated Name Only");
        assertThat(persisted.getPhone()).isEqualTo("+1234567890");
    }

    @Test
    @DisplayName("PATCH /users/me - Happy path: update phone only, fullName remains unchanged")
    void patchCurrentUser_happyPath_updatesPhoneOnly() throws Exception {
        User user = createAndPersistUser("patch2@example.com", "Original Name", "+1234567890", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "phone": "+9876543210"
            }
            """;

        mockMvc.perform(patch("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(user.getId().toString()))
            .andExpect(jsonPath("$.email").value("patch2@example.com"))
            .andExpect(jsonPath("$.fullName").value("Original Name"))
            .andExpect(jsonPath("$.phone").value("+9876543210"));

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getFullName()).isEqualTo("Original Name");
        assertThat(persisted.getPhone()).isEqualTo("+9876543210");
    }

    @Test
    @DisplayName("PATCH /users/me - Happy path: update both mutable fields simultaneously")
    void patchCurrentUser_happyPath_updatesBothFields() throws Exception {
        User user = createAndPersistUser("patch3@example.com", "Old Name", "+1111111111", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "fullName": "Brand New Name",
                "phone": "+9999999999"
            }
            """;

        mockMvc.perform(patch("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(user.getId().toString()))
            .andExpect(jsonPath("$.email").value("patch3@example.com"))
            .andExpect(jsonPath("$.fullName").value("Brand New Name"))
            .andExpect(jsonPath("$.phone").value("+9999999999"));

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getFullName()).isEqualTo("Brand New Name");
        assertThat(persisted.getPhone()).isEqualTo("+9999999999");
    }

    @Test
    @DisplayName("PATCH /users/me - Omitted fields: empty body leaves profile unchanged")
    void patchCurrentUser_omittedFields_remainUnchanged() throws Exception {
        User user = createAndPersistUser("patch4@example.com", "Preserved Name", "+1234567890", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = "{}";

        mockMvc.perform(patch("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(user.getId().toString()))
            .andExpect(jsonPath("$.email").value("patch4@example.com"))
            .andExpect(jsonPath("$.fullName").value("Preserved Name"))
            .andExpect(jsonPath("$.phone").value("+1234567890"));

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getFullName()).isEqualTo("Preserved Name");
        assertThat(persisted.getPhone()).isEqualTo("+1234567890");
    }

    @Test
    @DisplayName("PATCH /users/me - Validation failure: fullName exceeding 255 chars returns 400")
    void patchCurrentUser_validationError_fullNameTooLong() throws Exception {
        User user = createAndPersistUser("patch5@example.com", "Valid Name", "+1234567890", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String longName = "a".repeat(256);
        String requestBody = """
            {
                "fullName": "%s"
            }
            """.formatted(longName);

        mockMvc.perform(patch("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("Validation failed"));

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getFullName()).isEqualTo("Valid Name");
    }

    @Test
    @DisplayName("PATCH /users/me - Validation failure: phone exceeding 50 chars returns 400")
    void patchCurrentUser_validationError_phoneTooLong() throws Exception {
        User user = createAndPersistUser("patch6@example.com", "Valid Name", "+1234567890", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String longPhone = "1".repeat(51);
        String requestBody = """
            {
                "phone": "%s"
            }
            """.formatted(longPhone);

        mockMvc.perform(patch("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("Validation failed"));

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getPhone()).isEqualTo("+1234567890");
    }

    @Test
    @DisplayName("PATCH /users/me - Unauthenticated request returns 401 UNAUTHORIZED")
    void patchCurrentUser_unauthenticated_returns401() throws Exception {
        String requestBody = """
            {
                "fullName": "New Name"
            }
            """;

        mockMvc.perform(patch("/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PATCH /users/me - Non-existent user ID in valid token returns 404 USER_NOT_FOUND")
    void patchCurrentUser_userNotFound_returns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        String token = jwtService.generateToken(nonExistentId, "deleted@example.com", List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "fullName": "Ghost User"
            }
            """;

        mockMvc.perform(patch("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
            .andExpect(jsonPath("$.error.message").value("User with id '" + nonExistentId + "' not found"));
    }

    @Test
    @DisplayName("PATCH /users/me - Mass assignment immunity: protected fields (email, status, roles, id) cannot be changed")
    void patchCurrentUser_massAssignmentImmunity_ignoresProtectedFields() throws Exception {
        User user = createAndPersistUser("target@example.com", "Original Name", "+1234567890", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        UUID fakeId = UUID.randomUUID();
        String maliciousPayload = """
            {
                "id": "%s",
                "email": "hacker@evil.com",
                "status": "SUSPENDED",
                "isAdmin": true,
                "isCustomer": false,
                "isSeller": true,
                "password": "newHackedPassword123!",
                "passwordHash": "hackedHash",
                "fullName": "Safe Updated Name",
                "phone": "+9876543210"
            }
            """.formatted(fakeId);

        mockMvc.perform(patch("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(maliciousPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(user.getId().toString()))
            .andExpect(jsonPath("$.email").value("target@example.com"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.isAdmin").value(false))
            .andExpect(jsonPath("$.isCustomer").value(true))
            .andExpect(jsonPath("$.isSeller").value(false))
            .andExpect(jsonPath("$.fullName").value("Safe Updated Name"))
            .andExpect(jsonPath("$.phone").value("+9876543210"));

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(user.getId());
        assertThat(persisted.getEmail()).isEqualTo("target@example.com");
        assertThat(persisted.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(persisted.isAdmin()).isFalse();
        assertThat(persisted.isCustomer()).isTrue();
        assertThat(persisted.isSeller()).isFalse();
        assertThat(persisted.getFullName()).isEqualTo("Safe Updated Name");
        assertThat(persisted.getPhone()).isEqualTo("+9876543210");
        assertThat(persisted.getPasswordHash()).isEqualTo("hashed_pw_secret_123");
    }

    @Test
    @DisplayName("PATCH /users/me - Identity isolation: authenticated user cannot update another user's profile")
    void patchCurrentUser_identityIsolation_onlyUpdatesCurrentAuthenticatedUser() throws Exception {
        User userA = createAndPersistUser("userA_patch@example.com", "User Alpha", "+1111111111", true, false, false);
        User userB = createAndPersistUser("userB_patch@example.com", "User Beta", "+2222222222", true, false, false);

        String tokenA = jwtService.generateToken(userA.getId(), userA.getEmail(), List.of("ROLE_CUSTOMER"));

        String payload = """
            {
                "userId": "%s",
                "fullName": "Attempted Compromise"
            }
            """.formatted(userB.getId());

        mockMvc.perform(patch("/users/me")
                .param("userId", userB.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(userA.getId().toString()))
            .andExpect(jsonPath("$.fullName").value("Attempted Compromise"));

        // User A was updated
        User persistedA = userRepository.findById(userA.getId()).orElseThrow();
        assertThat(persistedA.getFullName()).isEqualTo("Attempted Compromise");

        // User B is completely untouched
        User persistedB = userRepository.findById(userB.getId()).orElseThrow();
        assertThat(persistedB.getFullName()).isEqualTo("User Beta");
        assertThat(persistedB.getPhone()).isEqualTo("+2222222222");
    }

    @Test
    @DisplayName("PATCH /users/me - Role versatility: SELLER and ADMIN can update own profile (Role: Any)")
    void patchCurrentUser_sellerAndAdminRoles_canUpdateOwnProfile() throws Exception {
        User seller = createAndPersistUser("seller_patch@example.com", "Seller Original", "+3333333333", true, true, false);
        String sellerToken = jwtService.generateToken(seller.getId(), seller.getEmail(), List.of("ROLE_SELLER"));

        mockMvc.perform(patch("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"Seller Updated\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Seller Updated"))
            .andExpect(jsonPath("$.isSeller").value(true));

        User admin = createAndPersistUser("admin_patch@example.com", "Admin Original", "+4444444444", true, false, true);
        String adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), List.of("ROLE_ADMIN"));

        mockMvc.perform(patch("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"+7777777777\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Admin Original"))
            .andExpect(jsonPath("$.phone").value("+7777777777"))
            .andExpect(jsonPath("$.isAdmin").value(true));
    }
}
