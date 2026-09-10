package com.omar.vendora.identity.service;

import com.omar.vendora.common.exception.InvalidCredentialsException;
import com.omar.vendora.common.exception.InvalidRefreshTokenException;
import com.omar.vendora.identity.dto.LoginRequest;
import com.omar.vendora.identity.dto.LoginResponse;
import com.omar.vendora.identity.dto.LogoutRequest;
import com.omar.vendora.identity.dto.RefreshTokenRequest;
import com.omar.vendora.identity.dto.RefreshTokenResponse;
import com.omar.vendora.identity.dto.RegisterRequest;
import com.omar.vendora.identity.dto.RegisterResponse;
import com.omar.vendora.identity.dto.RotatedToken;
import com.omar.vendora.security.JwtService;
import com.omar.vendora.users.dto.UserAuthDto;
import com.omar.vendora.users.dto.UserDto;
import com.omar.vendora.users.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    // Pre-computed BCrypt hash of "dummy" used to prevent timing attacks when email is not found
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final RefreshTokenService refreshTokenService;

    public AuthServiceImpl(
        UserService userService,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        LoginRateLimiter loginRateLimiter,
        RefreshTokenService refreshTokenService
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public RegisterResponse register(RegisterRequest request) {
        String passwordHash = passwordEncoder.encode(request.password());
        UserDto createdUser = userService.createUser(
            request.email(),
            passwordHash,
            request.fullName(),
            request.phone()
        );

        return new RegisterResponse(
            createdUser.id(),
            createdUser.email(),
            createdUser.fullName(),
            createdUser.phone(),
            createdUser.status(),
            createdUser.createdAt()
        );
    }

    @Override
    public LoginResponse login(LoginRequest request, String clientIp) {
        loginRateLimiter.acquire(clientIp);

        Optional<UserAuthDto> userOptional = userService.findByEmailForAuth(request.email());
        if (userOptional.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new InvalidCredentialsException();
        }

        UserAuthDto user = userOptional.get();
        if (!passwordEncoder.matches(request.password(), user.passwordHash()) || !"ACTIVE".equals(user.status())) {
            throw new InvalidCredentialsException();
        }

        List<String> roles = resolveRoles(user);
        String accessToken = jwtService.generateToken(user.id(), user.email(), roles);
        String refreshToken = refreshTokenService.createRefreshToken(user.id());

        return new LoginResponse(accessToken, refreshToken, jwtService.getTtlSeconds());
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        RotatedToken rotatedToken = refreshTokenService.rotateRefreshToken(request.refreshToken());

        UserAuthDto user = userService.findByIdForAuth(rotatedToken.userId())
            .orElseThrow(() -> new InvalidRefreshTokenException("User not found"));

        if (!"ACTIVE".equals(user.status())) {
            throw new InvalidRefreshTokenException("User account is inactive");
        }

        List<String> roles = resolveRoles(user);
        String newAccessToken = jwtService.generateToken(user.id(), user.email(), roles);

        return new RefreshTokenResponse(
            newAccessToken,
            rotatedToken.newRawRefreshToken(),
            jwtService.getTtlSeconds()
        );
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revokeRefreshToken(request.refreshToken());
    }

    private List<String> resolveRoles(UserAuthDto user) {
        List<String> roles = new ArrayList<>();
        if (user.isCustomer()) {
            roles.add("ROLE_CUSTOMER");
        }
        if (user.isSeller()) {
            roles.add("ROLE_SELLER");
        }
        if (user.isAdmin()) {
            roles.add("ROLE_ADMIN");
        }
        return roles;
    }
}
