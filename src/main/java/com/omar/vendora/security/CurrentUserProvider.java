package com.omar.vendora.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Provider component for retrieving the currently authenticated user's details
 * from Spring Security's SecurityContext.
 */
@Component
public class CurrentUserProvider {

    /**
     * Retrieves the authenticated user's ID as UUID.
     *
     * @return the authenticated user ID
     * @throws AccessDeniedException if the user is not authenticated or principal is invalid
     */
    public UUID getCurrentUserId() {
        return getCurrentUserIdOptional()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
    }

    /**
     * Retrieves the authenticated user's ID as an Optional UUID.
     *
     * @return Optional containing user UUID if authenticated, empty otherwise
     */
    public Optional<UUID> getCurrentUserIdOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        Object principal = auth.getPrincipal();
        if (principal == null) {
            return Optional.empty();
        }

        try {
            if (principal instanceof UUID uuid) {
                return Optional.of(uuid);
            }
            if (principal instanceof String str) {
                return Optional.of(UUID.fromString(str));
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    /**
     * Checks if the currently authenticated user has the specified role.
     *
     * @param role the role name (with or without 'ROLE_' prefix)
     * @return true if user has the role, false otherwise
     */
    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }

        String targetRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(targetRole));
    }

    /**
     * Checks if the currently authenticated user has the ADMIN role.
     *
     * @return true if admin, false otherwise
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Checks if a non-anonymous user is currently authenticated.
     *
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }
}
