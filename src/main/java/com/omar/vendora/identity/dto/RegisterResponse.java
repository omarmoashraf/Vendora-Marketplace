package com.omar.vendora.identity.dto;

import java.time.Instant;
import java.util.UUID;

public record RegisterResponse(
    UUID id,
    String email,
    String fullName,
    String phone,
    String status,
    Instant createdAt
) {}
