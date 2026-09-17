package com.omar.vendora.sellers.dto;

import com.omar.vendora.sellers.domain.ApplicationStatus;
import com.omar.vendora.sellers.domain.SellerApplication;

import java.time.Instant;
import java.util.UUID;

public record SellerApplicationResponse(
    UUID id,
    UUID userId,
    String businessName,
    String notes,
    ApplicationStatus status,
    Instant createdAt,
    Instant decidedAt,
    UUID decidedByAdminId
) {
    public static SellerApplicationResponse fromEntity(SellerApplication application) {
        return new SellerApplicationResponse(
            application.getId(),
            application.getUser() != null ? application.getUser().getId() : null,
            application.getBusinessName(),
            application.getNotes(),
            application.getStatus(),
            application.getCreatedAt(),
            application.getDecidedAt(),
            application.getDecidedByAdmin() != null ? application.getDecidedByAdmin().getId() : null
        );
    }
}
