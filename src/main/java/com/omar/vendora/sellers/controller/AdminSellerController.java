package com.omar.vendora.sellers.controller;

import com.omar.vendora.sellers.dto.SellerProfileResponse;
import com.omar.vendora.sellers.service.SellerProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/sellers")
public class AdminSellerController {

    private final SellerProfileService sellerProfileService;

    public AdminSellerController(SellerProfileService sellerProfileService) {
        this.sellerProfileService = sellerProfileService;
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SellerProfileResponse> suspendSeller(@PathVariable UUID id) {
        SellerProfileResponse response = sellerProfileService.suspendSeller(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SellerProfileResponse> reactivateSeller(@PathVariable UUID id) {
        SellerProfileResponse response = sellerProfileService.reactivateSeller(id);
        return ResponseEntity.ok(response);
    }
}
