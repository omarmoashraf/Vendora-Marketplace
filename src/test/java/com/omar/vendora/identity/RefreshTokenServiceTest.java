package com.omar.vendora.identity;

import com.omar.vendora.common.exception.InvalidRefreshTokenException;
import com.omar.vendora.identity.domain.RefreshToken;
import com.omar.vendora.identity.dto.RotatedToken;
import com.omar.vendora.identity.repository.RefreshTokenRepository;
import com.omar.vendora.identity.service.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenServiceImpl refreshTokenService;

    private static final long TTL_SECONDS = 604800L; // 7 days

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(refreshTokenRepository, TTL_SECONDS);
    }

    @Test
    @DisplayName("createRefreshToken - generates random token, hashes it, and persists entity")
    void testCreateRefreshToken_Success() {
        UUID userId = UUID.randomUUID();

        String rawToken = refreshTokenService.createRefreshToken(userId);

        assertThat(rawToken).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getTokenHash()).isEqualTo(refreshTokenService.hashToken(rawToken));
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("rotateRefreshToken - happy path revokes old token and issues a new one")
    void testRotateRefreshToken_Success() {
        UUID userId = UUID.randomUUID();
        String rawToken = "raw-old-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken existingToken = new RefreshToken(userId, tokenHash, Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existingToken));

        RotatedToken rotated = refreshTokenService.rotateRefreshToken(rawToken);

        assertThat(rotated).isNotNull();
        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(rotated.newRawRefreshToken()).isNotBlank().isNotEqualTo(rawToken);

        assertThat(existingToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(existingToken);
    }

    @Test
    @DisplayName("rotateRefreshToken - non-existent token throws InvalidRefreshTokenException")
    void testRotateRefreshToken_NotFound_ThrowsException() {
        String rawToken = "non-existent-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
            .isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessage("Invalid refresh token");
    }

    @Test
    @DisplayName("rotateRefreshToken - reuse of revoked token triggers session invalidation for user")
    void testRotateRefreshToken_ReuseDetection_RevokesAllSessions() {
        UUID userId = UUID.randomUUID();
        String rawToken = "already-revoked-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken revokedToken = new RefreshToken(userId, tokenHash, Instant.now().plusSeconds(3600));
        revokedToken.revoke();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
            .isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessageContaining("token was already revoked");

        verify(refreshTokenRepository).revokeAllByUserId(userId);
    }

    @Test
    @DisplayName("rotateRefreshToken - expired token throws InvalidRefreshTokenException")
    void testRotateRefreshToken_Expired_ThrowsException() {
        UUID userId = UUID.randomUUID();
        String rawToken = "expired-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken expiredToken = new RefreshToken(userId, tokenHash, Instant.now().minusSeconds(60));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
            .isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessage("Refresh token has expired");

        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    @DisplayName("revokeRefreshToken - sets revoked flag to true")
    void testRevokeRefreshToken_Success() {
        UUID userId = UUID.randomUUID();
        String rawToken = "token-to-revoke";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken token = new RefreshToken(userId, tokenHash, Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        refreshTokenService.revokeRefreshToken(rawToken);

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    @DisplayName("hashToken - generates deterministic SHA-256 hex string")
    void testHashToken_DeterministicSha256() {
        String token = "test-token-123";
        String hash1 = refreshTokenService.hashToken(token);
        String hash2 = refreshTokenService.hashToken(token);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // 256 bits = 32 bytes = 64 hex characters
    }
}
