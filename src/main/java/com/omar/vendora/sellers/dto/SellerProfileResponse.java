package com.omar.vendora.sellers.dto;

import com.omar.vendora.sellers.domain.SellerProfile;
import com.omar.vendora.sellers.domain.SellerProfileStatus;

import java.time.Instant;
import java.util.UUID;

public record SellerProfileResponse(
    UUID id,
    UUID userId,
    SellerProfileStatus status,
    String displayName,
    String payoutMethod,
    String payoutDetailsJson,
    Instant approvedAt
) {
    public static SellerProfileResponse fromEntity(SellerProfile profile) {
        return new SellerProfileResponse(
            profile.getId(),
            profile.getUser() != null ? profile.getUser().getId() : null,
            profile.getStatus(),
            profile.getDisplayName(),
            profile.getPayoutMethod(),
            profile.getPayoutDetailsJson(),
            profile.getApprovedAt()
        );
    }
}
