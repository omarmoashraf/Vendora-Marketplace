package com.omar.vendora.identity.service;

import com.omar.vendora.identity.dto.RotatedToken;

import java.util.UUID;

public interface RefreshTokenService {

    String createRefreshToken(UUID userId);

    RotatedToken rotateRefreshToken(String rawRefreshToken);

    void revokeRefreshToken(String rawRefreshToken);

    String hashToken(String rawToken);

    long getTtlSeconds();
}
