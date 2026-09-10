package com.omar.vendora.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OwnershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Authenticated SELLER can access their own resource (returns 200)")
    void seller_accessOwnResource_returns200() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String token = jwtService.generateToken(sellerId, "seller1@example.com", List.of("ROLE_SELLER"));

        mockMvc.perform(get("/api/test/rbac/resources/" + sellerId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.ownerId").value(sellerId.toString()));
    }

    @Test
    @DisplayName("Authenticated SELLER cannot access another seller's resource (returns 403 Forbidden)")
    void seller_accessAnotherSellerResource_returns403() throws Exception {
        UUID sellerA = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();
        String tokenA = jwtService.generateToken(sellerA, "sellerA@example.com", List.of("ROLE_SELLER"));

        // Seller A attempts to mutate / access Seller B's resource
        mockMvc.perform(get("/api/test/rbac/resources/" + sellerB)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.error.message").value("Access denied: you do not own this resource"));
    }

    @Test
    @DisplayName("Customer role fails route-level role check before reaching ownership check (returns 403 Forbidden)")
    void customerRole_accessResource_returns403() throws Exception {
        UUID customerId = UUID.randomUUID();
        String token = jwtService.generateToken(customerId, "customer@example.com", List.of("ROLE_CUSTOMER"));

        mockMvc.perform(get("/api/test/rbac/resources/" + customerId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.error.message").value("Access denied: insufficient permissions"));
    }

    @Test
    @DisplayName("Admin role can access another owner's resource via admin override (returns 200)")
    void admin_accessOtherOwnerResource_returns200() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        String adminToken = jwtService.generateToken(adminId, "admin@example.com", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/test/rbac/resources/" + sellerId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.ownerId").value(sellerId.toString()));
    }

    @Test
    @DisplayName("Unauthenticated request to ownership-protected resource returns 401 Unauthorized")
    void unauthenticated_accessResource_returns401() throws Exception {
        UUID resourceId = UUID.randomUUID();

        mockMvc.perform(get("/api/test/rbac/resources/" + resourceId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Authentication is required to access this resource"));
    }
}
