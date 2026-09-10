package com.omar.vendora.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password,

    @Size(max = 255, message = "Full name must not exceed 255 characters")
    String fullName,

    @Size(max = 50, message = "Phone must not exceed 50 characters")
    String phone
) {}
