package com.omar.vendora.identity.dto;

import java.util.UUID;

public record RotatedToken(
    UUID userId,
    String newRawRefreshToken
) {}
