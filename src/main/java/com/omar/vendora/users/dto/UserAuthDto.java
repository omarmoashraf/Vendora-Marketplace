package com.omar.vendora.users.dto;

import java.util.UUID;

public record UserAuthDto(
    UUID id,
    String email,
    String passwordHash,
    String status,
    boolean isAdmin,
    boolean isCustomer,
    boolean isSeller
) {}
