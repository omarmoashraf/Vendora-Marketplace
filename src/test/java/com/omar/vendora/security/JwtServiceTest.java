package com.omar.vendora.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "super-secure-secret-key-at-least-256-bits-long-for-testing!";
    private final long ttlSeconds = 900;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(objectMapper, secret, ttlSeconds);
    }

    @Test
    @DisplayName("generateToken - issues a well-formed 3-part JWT")
    void testGenerateToken_WellFormed() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        List<String> roles = List.of("ROLE_CUSTOMER");

        String token = jwtService.generateToken(userId, email, roles);

        assertThat(token).isNotBlank();
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractSubject(token)).isEqualTo(userId.toString());

        Map<String, Object> claims = jwtService.extractClaims(token);
        assertThat(claims.get("sub")).isEqualTo(userId.toString());
        assertThat(claims.get("email")).isEqualTo(email);
        assertThat(claims.get("roles")).isNotNull();
        assertThat(claims.get("exp")).isNotNull();
        assertThat(claims.get("iat")).isNotNull();
    }

    @Test
    @DisplayName("validateToken - rejects token with tampered payload")
    void testValidateToken_RejectsTamperedPayload() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "test@example.com", List.of("ROLE_CUSTOMER"));
        String[] parts = token.split("\\.");

        // Tamper with payload (substitute role to ROLE_ADMIN)
        String tamperedPayloadJson = "{\"sub\":\"" + userId + "\",\"email\":\"test@example.com\",\"roles\":[\"ROLE_ADMIN\"],\"iat\":1000,\"exp\":9999999999}";
        String tamperedPayloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayloadJson.getBytes(StandardCharsets.UTF_8));

        String tamperedToken = parts[0] + "." + tamperedPayloadB64 + "." + parts[2];

        assertThat(jwtService.validateToken(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("validateToken - rejects token with invalid signature")
    void testValidateToken_RejectsInvalidSignature() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "test@example.com", List.of("ROLE_CUSTOMER"));
        String[] parts = token.split("\\.");

        String invalidSignature = parts[2] + "corrupted";
        String tamperedToken = parts[0] + "." + parts[1] + "." + invalidSignature;

        assertThat(jwtService.validateToken(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("validateToken - rejects expired token")
    void testValidateToken_RejectsExpiredToken() {
        // JwtService with -10 second TTL creates an already expired token
        JwtService expiredJwtService = new JwtService(objectMapper, secret, -10);
        UUID userId = UUID.randomUUID();
        String expiredToken = expiredJwtService.generateToken(userId, "test@example.com", List.of("ROLE_CUSTOMER"));

        assertThat(jwtService.validateToken(expiredToken)).isFalse();
    }
}
