package com.omar.vendora.users.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

public record UpdateAddressRequest(
    @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
    String line1,

    @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
    String line2,

    @Size(max = 100, message = "City must not exceed 100 characters")
    String city,

    @Size(max = 100, message = "Region must not exceed 100 characters")
    String region,

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    @JsonAlias({"postal_code", "postalCode"})
    String postalCode,

    @Size(max = 100, message = "Country must not exceed 100 characters")
    String country,

    @Size(max = 50, message = "Phone must not exceed 50 characters")
    String phone,

    @JsonAlias({"is_default", "isDefault"})
    Boolean isDefault
) {}
