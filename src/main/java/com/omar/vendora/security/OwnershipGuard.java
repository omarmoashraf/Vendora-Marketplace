package com.omar.vendora.security;

import com.omar.vendora.common.exception.OwnershipViolationException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Reusable ownership-check component for business and service layer enforcement.
 * Enforces that non-admin callers can only access or mutate resources they own.
 */
@Component
public class OwnershipGuard {

    private final CurrentUserProvider currentUserProvider;

    public OwnershipGuard(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Checks if the authenticated user owns the resource identified by {@code resourceOwnerId}.
     * Callers with ROLE_ADMIN bypass ownership checks.
     *
     * @param resourceOwnerId the UUID of the owner of the resource
     * @throws OwnershipViolationException if the authenticated user does not own the resource and is not an admin
     */
    public void checkOwnership(UUID resourceOwnerId) {
        checkOwnership(resourceOwnerId, "Access denied: you do not own this resource");
    }

    /**
     * Checks if the authenticated user owns the resource identified by {@code resourceOwnerId}.
     * Callers with ROLE_ADMIN bypass ownership checks.
     *
     * @param resourceOwnerId the UUID of the owner of the resource
     * @param customErrorMessage custom message to include in the exception
     * @throws OwnershipViolationException if the authenticated user does not own the resource and is not an admin
     */
    public void checkOwnership(UUID resourceOwnerId, String customErrorMessage) {
        if (currentUserProvider.isAdmin()) {
            return;
        }

        UUID currentUserId = currentUserProvider.getCurrentUserId();
        if (resourceOwnerId == null || !Objects.equals(currentUserId, resourceOwnerId)) {
            throw new OwnershipViolationException(customErrorMessage);
        }
    }

    /**
     * Verifies that the given currentUserId matches the resourceOwnerId.
     * Callers with ROLE_ADMIN bypass the check.
     *
     * @param currentUserId the UUID of the caller/current user
     * @param resourceOwnerId the UUID of the resource owner
     * @throws OwnershipViolationException if IDs do not match and caller is not an admin
     */
    public void checkOwnership(UUID currentUserId, UUID resourceOwnerId) {
        if (currentUserProvider.isAdmin()) {
            return;
        }

        if (currentUserId == null || resourceOwnerId == null || !Objects.equals(currentUserId, resourceOwnerId)) {
            throw new OwnershipViolationException("Access denied: you do not own this resource");
        }
    }

    /**
     * Non-throwing ownership check.
     *
     * @param resourceOwnerId the UUID of the resource owner
     * @return true if caller is admin or owns the resource, false otherwise
     */
    public boolean isOwner(UUID resourceOwnerId) {
        if (currentUserProvider.isAdmin()) {
            return true;
        }

        return currentUserProvider.getCurrentUserIdOptional()
                .map(currentUserId -> Objects.equals(currentUserId, resourceOwnerId))
                .orElse(false);
    }
}
