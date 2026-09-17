package com.omar.vendora.sellers.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitSellerApplicationRequest(
    @NotBlank(message = "Business name is required")
    @Size(max = 255, message = "Business name must not exceed 255 characters")
    @JsonAlias({"business_name", "businessName"})
    String businessName,

    String notes
) {}
