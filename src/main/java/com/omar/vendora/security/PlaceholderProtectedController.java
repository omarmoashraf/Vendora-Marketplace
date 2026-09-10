package com.omar.vendora.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/test/rbac")
public class PlaceholderProtectedController {

    private final PlaceholderResourceService placeholderResourceService;

    public PlaceholderProtectedController(PlaceholderResourceService placeholderResourceService) {
        this.placeholderResourceService = placeholderResourceService;
    }

    @GetMapping("/seller")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Map<String, String>> sellerOnlyEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Seller access granted"));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> adminOnlyEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Admin access granted"));
    }

    @GetMapping("/resources/{ownerId}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> accessResource(@PathVariable UUID ownerId) {
        return ResponseEntity.ok(placeholderResourceService.mutateResource(ownerId));
    }
}
