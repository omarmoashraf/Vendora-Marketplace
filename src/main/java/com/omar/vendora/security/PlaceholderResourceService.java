package com.omar.vendora.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Service to demonstrate business-layer ownership verification.
 */
@Service
public class PlaceholderResourceService {

    private final OwnershipGuard ownershipGuard;

    public PlaceholderResourceService(OwnershipGuard ownershipGuard) {
        this.ownershipGuard = ownershipGuard;
    }

    /**
     * Simulates mutating or accessing an ownership-protected resource at the business layer.
     *
     * @param resourceOwnerId the UUID of the owner of the resource
     * @return result map if ownership check passes
     */
    public Map<String, String> mutateResource(UUID resourceOwnerId) {
        ownershipGuard.checkOwnership(resourceOwnerId);

        return Map.of(
            "status", "SUCCESS",
            "message", "Resource accessed successfully",
            "ownerId", resourceOwnerId.toString()
        );
    }
}
