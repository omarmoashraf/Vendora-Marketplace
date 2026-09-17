package com.omar.vendora.sellers.controller;

import com.omar.vendora.security.CurrentUserProvider;
import com.omar.vendora.sellers.dto.SellerApplicationResponse;
import com.omar.vendora.sellers.dto.SubmitSellerApplicationRequest;
import com.omar.vendora.sellers.service.SellerApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/seller-applications")
public class SellerApplicationController {

    private final SellerApplicationService sellerApplicationService;
    private final CurrentUserProvider currentUserProvider;

    public SellerApplicationController(SellerApplicationService sellerApplicationService,
                                       CurrentUserProvider currentUserProvider) {
        this.sellerApplicationService = sellerApplicationService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<SellerApplicationResponse> submitApplication(
            @Valid @RequestBody SubmitSellerApplicationRequest request) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        SellerApplicationResponse response = sellerApplicationService.submitApplication(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<SellerApplicationResponse> getMyApplication() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        SellerApplicationResponse response = sellerApplicationService.getMyApplication(currentUserId);
        return ResponseEntity.ok(response);
    }
}
