package com.omar.vendora.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.omar.vendora.identity.domain.RefreshToken;
import com.omar.vendora.identity.dto.LoginRequest;
import com.omar.vendora.identity.dto.LogoutRequest;
import com.omar.vendora.identity.dto.RefreshTokenRequest;
import com.omar.vendora.identity.dto.RegisterRequest;
import com.omar.vendora.identity.repository.RefreshTokenRepository;
import com.omar.vendora.identity.service.LoginRateLimiter;
import com.omar.vendora.security.JwtService;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @BeforeEach
    void setUp() {
        loginRateLimiter.reset();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /auth/register - Happy path: Successfully registers new user with 201 Created")
    void testRegisterUser_Success() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "john.doe@example.com",
            "SecureP@ssword123",
            "John Doe",
            "+1234567890"
        );

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.email").value("john.doe@example.com"))
            .andExpect(jsonPath("$.fullName").value("John Doe"))
            .andExpect(jsonPath("$.phone").value("+1234567890"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Optional<User> savedUserOpt = userRepository.findByEmail("john.doe@example.com");
        assertThat(savedUserOpt).isPresent();
        User savedUser = savedUserOpt.get();
        assertThat(passwordEncoder.matches("SecureP@ssword123", savedUser.getPasswordHash())).isTrue();
        assertThat(savedUser.getPasswordHash()).doesNotContain("SecureP@ssword123");
    }

    @Test
    @DisplayName("POST /auth/register - Duplicate email returns 409 Conflict")
    void testRegisterUser_DuplicateEmail_Returns409() throws Exception {
        RegisterRequest initialRequest = new RegisterRequest(
            "duplicate@example.com",
            "Password123!",
            "First User",
            null
        );

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialRequest)))
            .andExpect(status().isCreated());

        RegisterRequest duplicateRequest = new RegisterRequest(
            "duplicate@example.com",
            "DifferentPassword123!",
            "Second User",
            null
        );

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateRequest)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.error.message").isNotEmpty())
            .andExpect(jsonPath("$.error.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /auth/register - Missing email returns 400 Bad Request with field detail")
    void testRegisterUser_MissingEmail_Returns400() throws Exception {
        String json = """
            {
                "password": "ValidPassword123!",
                "fullName": "No Email"
            }
            """;

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[?(@.field == 'email')]").exists());
    }

    @Test
    @DisplayName("POST /auth/register - Invalid email format returns 400 Bad Request")
    void testRegisterUser_InvalidEmail_Returns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "not-an-email",
            "ValidPassword123!",
            "Invalid Email",
            null
        );

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[?(@.field == 'email')]").exists());
    }

    @Test
    @DisplayName("POST /auth/register - Missing password returns 400 Bad Request")
    void testRegisterUser_MissingPassword_Returns400() throws Exception {
        String json = """
            {
                "email": "test@example.com",
                "fullName": "No Password"
            }
            """;

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[?(@.field == 'password')]").exists());
    }

    @Test
    @DisplayName("POST /auth/register - Password shorter than 8 characters returns 400 Bad Request")
    void testRegisterUser_ShortPassword_Returns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "short@example.com",
            "short",
            "Short Pass",
            null
        );

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[?(@.field == 'password')]").exists());
    }

    @Test
    @DisplayName("POST /auth/login - Happy path: valid credentials returns 200 with JWT and refresh token")
    void testLogin_Success() throws Exception {
        RegisterRequest regRequest = new RegisterRequest(
            "login.user@example.com",
            "SecretPassword123!",
            "Login User",
            "+1234567890"
        );
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest)))
            .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("login.user@example.com", "SecretPassword123!");

        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(responseJson);
        String accessToken = root.get("accessToken").asText();

        // Validate JWT signature and claims
        assertThat(jwtService.validateToken(accessToken)).isTrue();
        Map<String, Object> claims = jwtService.extractClaims(accessToken);
        assertThat(claims.get("email")).isEqualTo("login.user@example.com");
        assertThat((String) claims.get("sub")).isNotEmpty();
    }

    @Test
    @DisplayName("POST /auth/login - Correct email + wrong password returns generic 401")
    void testLogin_WrongPassword_ReturnsGeneric401() throws Exception {
        RegisterRequest regRequest = new RegisterRequest(
            "wrong.pass@example.com",
            "CorrectPassword123!",
            "Wrong Pass User",
            null
        );
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest)))
            .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("wrong.pass@example.com", "IncorrectPassword123!");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.error.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /auth/login - Non-existent email returns identical generic 401")
    void testLogin_NonExistentEmail_ReturnsGeneric401() throws Exception {
        LoginRequest loginRequest = new LoginRequest("doesnotexist@example.com", "AnyPassword123!");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.error.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /auth/login - Missing email returns 400 Bad Request")
    void testLogin_MissingEmail_Returns400() throws Exception {
        String json = """
            {
                "password": "Password123!"
            }
            """;

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[?(@.field == 'email')]").exists());
    }

    @Test
    @DisplayName("POST /auth/login - Rate limiting triggers after 10 consecutive failed attempts")
    void testLogin_RateLimiting_TriggersAfter10Attempts() throws Exception {
        LoginRequest loginRequest = new LoginRequest("nonexistent@example.com", "WrongPassword!");

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.error.message").value("Too many login attempts. Please try again later."));
    }

    @Test
    @DisplayName("POST /auth/refresh - Happy path: validates and rotates refresh token, returning new access token")
    void testRefresh_Success() throws Exception {
        RegisterRequest regRequest = new RegisterRequest(
            "refresh.user@example.com",
            "SecretPassword123!",
            "Refresh User",
            null
        );
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest)))
            .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("refresh.user@example.com", "SecretPassword123!");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode loginNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String initialRefreshToken = loginNode.get("refreshToken").asText();

        // Perform refresh
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(initialRefreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andReturn();

        JsonNode refreshNode = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newRefreshToken = refreshNode.get("refreshToken").asText();

        // Ensure token was rotated
        assertThat(newRefreshToken).isNotEqualTo(initialRefreshToken);

        // Verify the new access token is valid
        String newAccessToken = refreshNode.get("accessToken").asText();
        assertThat(jwtService.validateToken(newAccessToken)).isTrue();
    }

    @Test
    @DisplayName("POST /auth/refresh - Reusing an already-rotated token triggers reuse detection and revokes session")
    void testRefresh_ReuseDetection() throws Exception {
        RegisterRequest regRequest = new RegisterRequest(
            "reuse.user@example.com",
            "SecretPassword123!",
            "Reuse User",
            null
        );
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest)))
            .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("reuse.user@example.com", "SecretPassword123!");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode loginNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String originalRefreshToken = loginNode.get("refreshToken").asText();

        // First refresh: consumes original token and issues second token
        RefreshTokenRequest firstRefresh = new RefreshTokenRequest(originalRefreshToken);
        MvcResult firstRefreshResult = mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstRefresh)))
            .andExpect(status().isOk())
            .andReturn();

        String secondRefreshToken = objectMapper.readTree(firstRefreshResult.getResponse().getContentAsString()
        ).get("refreshToken").asText();

        // Reusing original (already rotated) token -> must fail with 401
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstRefresh)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));

        // Reuse detection should also have invalidated the second refresh token!
        RefreshTokenRequest secondRefresh = new RefreshTokenRequest(secondRefreshToken);
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondRefresh)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("POST /auth/refresh - Concurrent requests with identical token: only one succeeds, other triggers reuse detection and 401")
    void testRefresh_ConcurrentRequests_OnlyOneSucceeds() throws Exception {
        RegisterRequest regRequest = new RegisterRequest(
            "concurrent.user@example.com",
            "SecretPassword123!",
            "Concurrent User",
            null
        );
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest)))
            .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("concurrent.user@example.com", "SecretPassword123!");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode loginNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String refreshToken = loginNode.get("refreshToken").asText();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                RefreshTokenRequest refreshReq = new RefreshTokenRequest(refreshToken);
                return mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                    .andReturn();
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        int successCount = 0;
        int failureCount = 0;

        for (Future<MvcResult> future : futures) {
            MvcResult result = future.get(10, TimeUnit.SECONDS);
            int status = result.getResponse().getStatus();
            if (status == 200) {
                successCount++;
                JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
                assertThat(responseNode.get("accessToken").asText()).isNotEmpty();
                assertThat(responseNode.get("refreshToken").asText()).isNotEmpty();
            } else if (status == 401) {
                failureCount++;
                JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
                assertThat(responseNode.get("error").get("code").asText()).isEqualTo("INVALID_REFRESH_TOKEN");
            }
        }

        executor.shutdown();

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /auth/logout - Revokes token; subsequent refresh fails with 401")
    void testLogout_RevokesToken() throws Exception {
        RegisterRequest regRequest = new RegisterRequest(
            "logout.user@example.com",
            "SecretPassword123!",
            "Logout User",
            null
        );
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest)))
            .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("logout.user@example.com", "SecretPassword123!");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
            .get("refreshToken").asText();

        // Logout
        LogoutRequest logoutRequest = new LogoutRequest(refreshToken);
        mockMvc.perform(post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutRequest)))
            .andExpect(status().isNoContent());

        // Refresh with logged-out token must fail
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("POST /auth/refresh - Expired refresh token is rejected with 401")
    void testRefresh_ExpiredToken_Returns401() throws Exception {
        RegisterRequest regRequest = new RegisterRequest(
            "expired.user@example.com",
            "SecretPassword123!",
            "Expired User",
            null
        );
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest)))
            .andExpect(status().isCreated());

        User user = userRepository.findByEmail("expired.user@example.com").orElseThrow();

        // Insert an already expired token directly
        String rawToken = "expired-raw-token";
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        String sha256Hex = java.util.HexFormat.of().formatHex(md.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        RefreshToken expiredEntity = new RefreshToken(user.getId(), sha256Hex, Instant.now().minusSeconds(3600));
        refreshTokenRepository.save(expiredEntity);

        RefreshTokenRequest request = new RefreshTokenRequest(rawToken);
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("POST /auth/refresh - Non-existent refresh token is rejected with 401")
    void testRefresh_NonExistentToken_Returns401() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("non-existent-refresh-token");
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"))
            .andExpect(jsonPath("$.error.message").value("Invalid refresh token"));
    }

    @Test
    @DisplayName("POST /auth/refresh - Blank token returns 400 Bad Request")
    void testRefresh_BlankToken_Returns400() throws Exception {
        String json = """
            {
                "refreshToken": "   "
            }
            """;

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[?(@.field == 'refreshToken')]").exists());
    }

    @Test
    @DisplayName("POST /auth/logout - Blank token returns 400 Bad Request")
    void testLogout_BlankToken_Returns400() throws Exception {
        String json = """
            {
                "refreshToken": ""
            }
            """;

        mockMvc.perform(post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[?(@.field == 'refreshToken')]").exists());
    }
}
