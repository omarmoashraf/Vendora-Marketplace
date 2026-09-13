package com.omar.vendora.users.service;

import com.omar.vendora.common.exception.AddressNotFoundException;
import com.omar.vendora.common.exception.UserNotFoundException;
import com.omar.vendora.security.OwnershipGuard;
import com.omar.vendora.users.domain.Address;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.dto.AddressResponse;
import com.omar.vendora.users.dto.CreateAddressRequest;
import com.omar.vendora.users.dto.UpdateAddressRequest;
import com.omar.vendora.users.repository.AddressRepository;
import com.omar.vendora.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final OwnershipGuard ownershipGuard;

    public AddressServiceImpl(AddressRepository addressRepository,
                              UserRepository userRepository,
                              OwnershipGuard ownershipGuard) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.ownershipGuard = ownershipGuard;
    }

    @Override
    @Transactional
    public AddressResponse createAddress(UUID userId, CreateAddressRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        boolean isDefault = request.isDefault() != null && request.isDefault();

        if (isDefault) {
            addressRepository.findDefaultByUserId(userId).ifPresent(existingDefault -> {
                existingDefault.setDefault(false);
                addressRepository.saveAndFlush(existingDefault);
            });
        }

        Address address = new Address(
            user,
            request.line1(),
            request.line2(),
            request.city(),
            request.region(),
            request.postalCode(),
            request.country(),
            request.phone(),
            isDefault
        );

        Address savedAddress = addressRepository.save(address);
        return AddressResponse.fromEntity(savedAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddressesByUserId(UUID userId) {
        return addressRepository.findAllByUserId(userId)
            .stream()
            .map(AddressResponse::fromEntity)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getAddressById(UUID addressId) {
        Address address = addressRepository.findById(addressId)
            .orElseThrow(() -> new AddressNotFoundException(addressId));

        ownershipGuard.checkOwnership(address.getUser().getId());

        return AddressResponse.fromEntity(address);
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(UUID addressId, UpdateAddressRequest request) {
        Address address = addressRepository.findById(addressId)
            .orElseThrow(() -> new AddressNotFoundException(addressId));

        ownershipGuard.checkOwnership(address.getUser().getId());

        if (request.line1() != null) {
            address.setLine1(request.line1());
        }
        if (request.line2() != null) {
            address.setLine2(request.line2());
        }
        if (request.city() != null) {
            address.setCity(request.city());
        }
        if (request.region() != null) {
            address.setRegion(request.region());
        }
        if (request.postalCode() != null) {
            address.setPostalCode(request.postalCode());
        }
        if (request.country() != null) {
            address.setCountry(request.country());
        }
        if (request.phone() != null) {
            address.setPhone(request.phone());
        }
        if (request.isDefault() != null) {
            if (Boolean.TRUE.equals(request.isDefault())) {
                addressRepository.findDefaultByUserId(address.getUser().getId()).ifPresent(existingDefault -> {
                    if (!existingDefault.getId().equals(addressId)) {
                        existingDefault.setDefault(false);
                        addressRepository.saveAndFlush(existingDefault);
                    }
                });
                address.setDefault(true);
            } else {
                address.setDefault(false);
            }
        }

        Address savedAddress = addressRepository.save(address);
        return AddressResponse.fromEntity(savedAddress);
    }

    @Override
    @Transactional
    public void deleteAddress(UUID addressId) {
        Address address = addressRepository.findById(addressId)
            .orElseThrow(() -> new AddressNotFoundException(addressId));

        ownershipGuard.checkOwnership(address.getUser().getId());

        addressRepository.delete(address);
    }
}
