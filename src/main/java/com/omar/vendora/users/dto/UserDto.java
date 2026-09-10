package com.omar.vendora.users.dto;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
    UUID id,
    String email,
    String fullName,
    String phone,
    String status,
    boolean isAdmin,
    boolean isCustomer,
    boolean isSeller,
    Instant createdAt
) {}
