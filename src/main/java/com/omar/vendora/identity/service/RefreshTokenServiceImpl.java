package com.omar.vendora.identity.service;

import com.omar.vendora.common.exception.InvalidRefreshTokenException;
import com.omar.vendora.identity.domain.RefreshToken;
import com.omar.vendora.identity.dto.RotatedToken;
import com.omar.vendora.identity.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenTtlSeconds;

    public RefreshTokenServiceImpl(
        RefreshTokenRepository refreshTokenRepository,
        @Value("${security.jwt.refresh-token-ttl-seconds:604800}") long refreshTokenTtlSeconds
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Override
    @Transactional
    public String createRefreshToken(UUID userId) {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(refreshTokenTtlSeconds);

        RefreshToken refreshToken = new RefreshToken(userId, tokenHash, expiresAt);
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotatedToken rotateRefreshToken(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        Optional<RefreshToken> tokenOptional = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOptional.isEmpty()) {
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }

        RefreshToken token = tokenOptional.get();

        if (token.isRevoked()) {
            log.warn("Refresh token reuse detected for userId={}! Revoking all sessions.", token.getUserId());
            refreshTokenRepository.revokeAllByUserId(token.getUserId());
            throw new InvalidRefreshTokenException("Invalid refresh token: token was already revoked");
        }

        if (token.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        // Revoke the current token
        token.revoke();
        refreshTokenRepository.save(token);

        // Issue a new token
        String newRawToken = createRefreshToken(token.getUserId());

        return new RotatedToken(token.getUserId(), newRawToken);
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke();
                refreshTokenRepository.save(token);
            }
        });
    }

    @Override
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    @Override
    public long getTtlSeconds() {
        return refreshTokenTtlSeconds;
    }
}
