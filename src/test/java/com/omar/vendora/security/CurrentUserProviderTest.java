package com.omar.vendora.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserProviderTest {

    private CurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUp() {
        currentUserProvider = new CurrentUserProvider();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getCurrentUserId returns UUID when authenticated with UUID string principal")
    void getCurrentUserId_authenticated_returnsUuid() {
        UUID userId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SELLER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        UUID result = currentUserProvider.getCurrentUserId();
        assertThat(result).isEqualTo(userId);
    }

    @Test
    @DisplayName("getCurrentUserId returns UUID when authenticated with UUID object principal")
    void getCurrentUserId_authenticatedWithUuidObject_returnsUuid() {
        UUID userId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SELLER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        UUID result = currentUserProvider.getCurrentUserId();
        assertThat(result).isEqualTo(userId);
    }

    @Test
    @DisplayName("getCurrentUserId throws AccessDeniedException when unauthenticated")
    void getCurrentUserId_unauthenticated_throwsAccessDeniedException() {
        assertThatThrownBy(() -> currentUserProvider.getCurrentUserId())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not authenticated");
    }

    @Test
    @DisplayName("getCurrentUserId throws AccessDeniedException when anonymous token")
    void getCurrentUserId_anonymous_throwsAccessDeniedException() {
        AnonymousAuthenticationToken anonymousToken = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(anonymousToken);

        assertThatThrownBy(() -> currentUserProvider.getCurrentUserId())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("getCurrentUserIdOptional returns empty when unauthenticated or invalid principal")
    void getCurrentUserIdOptional_unauthenticated_returnsEmpty() {
        Optional<UUID> result = currentUserProvider.getCurrentUserIdOptional();
        assertThat(result).isEmpty();

        // With invalid non-UUID string principal
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "not-a-uuid", null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThat(currentUserProvider.getCurrentUserIdOptional()).isEmpty();
    }

    @Test
    @DisplayName("hasRole returns true for matching role regardless of ROLE_ prefix")
    void hasRole_matchingRole_returnsTrue() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_SELLER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(currentUserProvider.hasRole("ADMIN")).isTrue();
        assertThat(currentUserProvider.hasRole("ROLE_ADMIN")).isTrue();
        assertThat(currentUserProvider.hasRole("SELLER")).isTrue();
        assertThat(currentUserProvider.hasRole("CUSTOMER")).isFalse();
        assertThat(currentUserProvider.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("isAuthenticated returns true for authenticated and false for unauthenticated/anonymous")
    void isAuthenticated_checksContext() {
        assertThat(currentUserProvider.isAuthenticated()).isFalse();

        AnonymousAuthenticationToken anonymousToken = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(anonymousToken);
        assertThat(currentUserProvider.isAuthenticated()).isFalse();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThat(currentUserProvider.isAuthenticated()).isTrue();
    }
}
