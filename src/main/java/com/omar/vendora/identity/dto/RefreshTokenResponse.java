package com.omar.vendora.identity.dto;

public record RefreshTokenResponse(
    String accessToken,
    String refreshToken,
    long expiresIn
) {}
