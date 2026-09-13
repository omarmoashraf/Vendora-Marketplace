package com.omar.vendora.users.dto;

import com.omar.vendora.users.domain.Address;

import java.util.UUID;

public record AddressResponse(
    UUID id,
    UUID userId,
    String line1,
    String line2,
    String city,
    String region,
    String postalCode,
    String country,
    String phone,
    boolean isDefault
) {
    public static AddressResponse fromEntity(Address address) {
        return new AddressResponse(
            address.getId(),
            address.getUser() != null ? address.getUser().getId() : null,
            address.getLine1(),
            address.getLine2(),
            address.getCity(),
            address.getRegion(),
            address.getPostalCode(),
            address.getCountry(),
            address.getPhone(),
            address.isDefault()
        );
    }
}
