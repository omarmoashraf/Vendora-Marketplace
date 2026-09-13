package com.omar.vendora.users.controller;

import com.omar.vendora.security.CurrentUserProvider;
import com.omar.vendora.users.dto.AddressResponse;
import com.omar.vendora.users.dto.CreateAddressRequest;
import com.omar.vendora.users.dto.UpdateAddressRequest;
import com.omar.vendora.users.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users/me/addresses")
public class AddressController {

    private final AddressService addressService;
    private final CurrentUserProvider currentUserProvider;

    public AddressController(AddressService addressService, CurrentUserProvider currentUserProvider) {
        this.addressService = addressService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AddressResponse> createAddress(@Valid @RequestBody CreateAddressRequest request) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        AddressResponse response = addressService.createAddress(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<AddressResponse>> getAddresses() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        List<AddressResponse> addresses = addressService.getAddressesByUserId(currentUserId);
        return ResponseEntity.ok(addresses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AddressResponse> getAddressById(@PathVariable UUID id) {
        AddressResponse address = addressService.getAddressById(id);
        return ResponseEntity.ok(address);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AddressResponse> updateAddress(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAddressRequest request) {
        AddressResponse response = addressService.updateAddress(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> deleteAddress(@PathVariable UUID id) {
        addressService.deleteAddress(id);
        return ResponseEntity.noContent().build();
    }
}
