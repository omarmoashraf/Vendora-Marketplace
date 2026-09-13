package com.omar.vendora.users.service;

import com.omar.vendora.users.dto.AddressResponse;
import com.omar.vendora.users.dto.CreateAddressRequest;
import com.omar.vendora.users.dto.UpdateAddressRequest;

import java.util.List;
import java.util.UUID;

public interface AddressService {

    AddressResponse createAddress(UUID userId, CreateAddressRequest request);

    List<AddressResponse> getAddressesByUserId(UUID userId);

    AddressResponse getAddressById(UUID addressId);

    AddressResponse updateAddress(UUID addressId, UpdateAddressRequest request);

    void deleteAddress(UUID addressId);
}
