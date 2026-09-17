package com.omar.vendora.sellers.controller;

import com.omar.vendora.security.CurrentUserProvider;
import com.omar.vendora.sellers.dto.RejectSellerApplicationRequest;
import com.omar.vendora.sellers.dto.SellerApplicationResponse;
import com.omar.vendora.sellers.service.SellerApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/seller-applications")
public class AdminSellerApplicationController {

    private final SellerApplicationService sellerApplicationService;
    private final CurrentUserProvider currentUserProvider;

    public AdminSellerApplicationController(SellerApplicationService sellerApplicationService,
                                            CurrentUserProvider currentUserProvider) {
        this.sellerApplicationService = sellerApplicationService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SellerApplicationResponse> approveApplication(@PathVariable UUID id) {
        UUID adminId = currentUserProvider.getCurrentUserId();
        SellerApplicationResponse response = sellerApplicationService.approveApplication(id, adminId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SellerApplicationResponse> rejectApplication(
            @PathVariable UUID id,
            @RequestBody(required = false) RejectSellerApplicationRequest request) {
        UUID adminId = currentUserProvider.getCurrentUserId();
        String reason = (request != null) ? request.reason() : null;
        SellerApplicationResponse response = sellerApplicationService.rejectApplication(id, adminId, reason);
        return ResponseEntity.ok(response);
    }
}
