package com.omar.vendora.identity.dto;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn
) {
    public LoginResponse(String accessToken, long expiresIn) {
        this(accessToken, null, expiresIn);
    }
}
