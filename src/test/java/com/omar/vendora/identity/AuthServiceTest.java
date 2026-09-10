package com.omar.vendora.identity;

import com.omar.vendora.common.exception.InvalidCredentialsException;
import com.omar.vendora.common.exception.InvalidRefreshTokenException;
import com.omar.vendora.common.exception.RateLimitExceededException;
import com.omar.vendora.identity.dto.LoginRequest;
import com.omar.vendora.identity.dto.LoginResponse;
import com.omar.vendora.identity.dto.LogoutRequest;
import com.omar.vendora.identity.dto.RefreshTokenRequest;
import com.omar.vendora.identity.dto.RefreshTokenResponse;
import com.omar.vendora.identity.dto.RegisterRequest;
import com.omar.vendora.identity.dto.RegisterResponse;
import com.omar.vendora.identity.dto.RotatedToken;
import com.omar.vendora.identity.service.AuthServiceImpl;
import com.omar.vendora.identity.service.LoginRateLimiter;
import com.omar.vendora.identity.service.RefreshTokenService;
import com.omar.vendora.security.JwtService;
import com.omar.vendora.users.dto.UserAuthDto;
import com.omar.vendora.users.dto.UserDto;
import com.omar.vendora.users.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("AuthService.register - hashes password and delegates to UserService")
    void testRegister_HashesPasswordAndDelegates() {
        RegisterRequest request = new RegisterRequest(
            "alice@example.com",
            "PlainPassword123!",
            "Alice Smith",
            "+9876543210"
        );

        UUID generatedId = UUID.randomUUID();
        Instant now = Instant.now();
        String hashedPassword = "$2a$10$encodedHashDummy";

        when(passwordEncoder.encode("PlainPassword123!")).thenReturn(hashedPassword);
        when(userService.createUser(eq("alice@example.com"), eq(hashedPassword), eq("Alice Smith"), eq("+9876543210")))
            .thenReturn(new UserDto(generatedId, "alice@example.com", "Alice Smith", "+9876543210", "ACTIVE", false, true, false, now));

        RegisterResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(generatedId);
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.fullName()).isEqualTo("Alice Smith");
        assertThat(response.phone()).isEqualTo("+9876543210");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.createdAt()).isEqualTo(now);

        verify(passwordEncoder).encode("PlainPassword123!");
        verify(userService).createUser("alice@example.com", hashedPassword, "Alice Smith", "+9876543210");
    }

    @Test
    @DisplayName("AuthService.login - successful login returns LoginResponse with JWT and refresh token")
    void testLogin_Success() {
        LoginRequest request = new LoginRequest("bob@example.com", "Secret123!");
        UUID userId = UUID.randomUUID();
        String passwordHash = "$2a$10$hashedBobPassword";
        UserAuthDto userAuthDto = new UserAuthDto(userId, "bob@example.com", passwordHash, "ACTIVE", false, true, false);

        when(userService.findByEmailForAuth("bob@example.com")).thenReturn(Optional.of(userAuthDto));
        when(passwordEncoder.matches("Secret123!", passwordHash)).thenReturn(true);
        when(jwtService.generateToken(eq(userId), eq("bob@example.com"), anyList())).thenReturn("mock.jwt.token");
        when(jwtService.getTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(eq(userId))).thenReturn("mock.refresh.token");

        LoginResponse response = authService.login(request, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.refreshToken()).isEqualTo("mock.refresh.token");
        assertThat(response.expiresIn()).isEqualTo(900L);

        verify(loginRateLimiter).acquire("127.0.0.1");
        verify(refreshTokenService).createRefreshToken(userId);
    }

    @Test
    @DisplayName("AuthService.login - user with Customer and Seller roles includes both in JWT")
    void testLogin_CustomerAndSellerRoles() {
        LoginRequest request = new LoginRequest("seller@example.com", "Secret123!");
        UUID userId = UUID.randomUUID();
        String passwordHash = "$2a$10$hashedSellerPassword";
        UserAuthDto userAuthDto = new UserAuthDto(userId, "seller@example.com", passwordHash, "ACTIVE", false, true, true);

        when(userService.findByEmailForAuth("seller@example.com")).thenReturn(Optional.of(userAuthDto));
        when(passwordEncoder.matches("Secret123!", passwordHash)).thenReturn(true);
        when(jwtService.generateToken(eq(userId), eq("seller@example.com"), anyList())).thenReturn("mock.jwt.token");
        when(jwtService.getTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(eq(userId))).thenReturn("mock.refresh.token");

        authService.login(request, "127.0.0.1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> rolesCaptor = ArgumentCaptor.forClass(List.class);
        verify(jwtService).generateToken(eq(userId), eq("seller@example.com"), rolesCaptor.capture());
        assertThat(rolesCaptor.getValue()).containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_SELLER");
    }

    @Test
    @DisplayName("AuthService.login - wrong password throws InvalidCredentialsException")
    void testLogin_WrongPassword_ThrowsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest("bob@example.com", "WrongPassword!");
        UUID userId = UUID.randomUUID();
        String passwordHash = "$2a$10$hashedBobPassword";
        UserAuthDto userAuthDto = new UserAuthDto(userId, "bob@example.com", passwordHash, "ACTIVE", false, true, false);

        when(userService.findByEmailForAuth("bob@example.com")).thenReturn(Optional.of(userAuthDto));
        when(passwordEncoder.matches("WrongPassword!", passwordHash)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1"))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("AuthService.login - non-existent email throws InvalidCredentialsException")
    void testLogin_NonExistentEmail_ThrowsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest("unknown@example.com", "AnyPassword123!");

        when(userService.findByEmailForAuth("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1"))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("AuthService.login - rate limit exceeded propagates exception")
    void testLogin_RateLimitExceeded() {
        LoginRequest request = new LoginRequest("bob@example.com", "Secret123!");

        doThrow(new RateLimitExceededException()).when(loginRateLimiter).acquire("192.168.1.1");

        assertThatThrownBy(() -> authService.login(request, "192.168.1.1"))
            .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("AuthService.refresh - success rotates token and returns new JWT and refresh token")
    void testRefresh_Success() {
        UUID userId = UUID.randomUUID();
        RefreshTokenRequest request = new RefreshTokenRequest("old.raw.token");
        RotatedToken rotatedToken = new RotatedToken(userId, "new.raw.token");
        UserAuthDto userAuthDto = new UserAuthDto(userId, "charlie@example.com", "hashedPass", "ACTIVE", false, true, false);

        when(refreshTokenService.rotateRefreshToken("old.raw.token")).thenReturn(rotatedToken);
        when(userService.findByIdForAuth(userId)).thenReturn(Optional.of(userAuthDto));
        when(jwtService.generateToken(eq(userId), eq("charlie@example.com"), anyList())).thenReturn("new.access.token");
        when(jwtService.getTtlSeconds()).thenReturn(900L);

        RefreshTokenResponse response = authService.refresh(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new.access.token");
        assertThat(response.refreshToken()).isEqualTo("new.raw.token");
        assertThat(response.expiresIn()).isEqualTo(900L);

        verify(refreshTokenService).rotateRefreshToken("old.raw.token");
        verify(userService).findByIdForAuth(userId);
    }

    @Test
    @DisplayName("AuthService.refresh - inactive user throws InvalidRefreshTokenException")
    void testRefresh_InactiveUser_ThrowsException() {
        UUID userId = UUID.randomUUID();
        RefreshTokenRequest request = new RefreshTokenRequest("old.raw.token");
        RotatedToken rotatedToken = new RotatedToken(userId, "new.raw.token");
        UserAuthDto userAuthDto = new UserAuthDto(userId, "inactive@example.com", "hashedPass", "SUSPENDED", false, true, false);

        when(refreshTokenService.rotateRefreshToken("old.raw.token")).thenReturn(rotatedToken);
        when(userService.findByIdForAuth(userId)).thenReturn(Optional.of(userAuthDto));

        assertThatThrownBy(() -> authService.refresh(request))
            .isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessage("User account is inactive");
    }

    @Test
    @DisplayName("AuthService.logout - revokes refresh token")
    void testLogout_RevokesToken() {
        LogoutRequest request = new LogoutRequest("logout.raw.token");

        authService.logout(request);

        verify(refreshTokenService).revokeRefreshToken("logout.raw.token");
    }
}
