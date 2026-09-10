package com.omar.vendora.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MethodSecurityRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Unauthenticated request to Seller endpoint returns 401")
    void unauthenticated_toSellerEndpoint_returns401() throws Exception {
        mockMvc.perform(get("/api/test/rbac/seller"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Unauthenticated request to Admin endpoint returns 401")
    void unauthenticated_toAdminEndpoint_returns401() throws Exception {
        mockMvc.perform(get("/api/test/rbac/admin"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Customer role accessing Seller endpoint returns 403")
    void customerRole_toSellerEndpoint_returns403() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID(), "customer@example.com", List.of("ROLE_CUSTOMER"));

        mockMvc.perform(get("/api/test/rbac/seller")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Customer role accessing Admin endpoint returns 403")
    void customerRole_toAdminEndpoint_returns403() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID(), "customer@example.com", List.of("ROLE_CUSTOMER"));

        mockMvc.perform(get("/api/test/rbac/admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Seller role accessing Seller endpoint returns 200")
    void sellerRole_toSellerEndpoint_returns200() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID(), "seller@example.com", List.of("ROLE_SELLER"));

        mockMvc.perform(get("/api/test/rbac/seller")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Seller access granted"));
    }

    @Test
    @DisplayName("Seller role accessing Admin endpoint returns 403")
    void sellerRole_toAdminEndpoint_returns403() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID(), "seller@example.com", List.of("ROLE_SELLER"));

        mockMvc.perform(get("/api/test/rbac/admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Admin role accessing Admin endpoint returns 200")
    void adminRole_toAdminEndpoint_returns200() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID(), "admin@example.com", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/test/rbac/admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Admin access granted"));
    }

    @Test
    @DisplayName("User with simultaneous Customer and Seller roles can access Seller endpoint")
    void multiRoleCustomerAndSeller_canAccessSellerEndpoint() throws Exception {
        String token = jwtService.generateToken(
            UUID.randomUUID(),
            "customer_seller@example.com",
            List.of("ROLE_CUSTOMER", "ROLE_SELLER")
        );

        mockMvc.perform(get("/api/test/rbac/seller")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Seller access granted"));
    }

    @Test
    @DisplayName("Tampered JWT signature is rejected with 401")
    void tamperedJwt_returns401() throws Exception {
        String validToken = jwtService.generateToken(UUID.randomUUID(), "seller@example.com", List.of("ROLE_SELLER"));
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "abcde";

        mockMvc.perform(get("/api/test/rbac/seller")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Malformed token is rejected with 401")
    void malformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/test/rbac/seller")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
