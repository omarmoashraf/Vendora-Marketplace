package com.omar.vendora.users.service;

import com.omar.vendora.common.exception.AddressNotFoundException;
import com.omar.vendora.common.exception.OwnershipViolationException;
import com.omar.vendora.common.exception.UserNotFoundException;
import com.omar.vendora.security.OwnershipGuard;
import com.omar.vendora.users.domain.Address;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.dto.AddressResponse;
import com.omar.vendora.users.dto.CreateAddressRequest;
import com.omar.vendora.users.dto.UpdateAddressRequest;
import com.omar.vendora.users.repository.AddressRepository;
import com.omar.vendora.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OwnershipGuard ownershipGuard;

    @InjectMocks
    private AddressServiceImpl addressService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new User("customer@example.com", "hashed_password", "Customer Name", "+1234567890");
        ReflectionTestUtils.setField(testUser, "id", userId);
    }

    @Test
    @DisplayName("createAddress - Success with all fields")
    void createAddress_success_withAllFields() {
        CreateAddressRequest request = new CreateAddressRequest(
            "123 Main St",
            "Apt 4B",
            "New York",
            "NY",
            "10001",
            "USA",
            "+1234567890",
            true
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address a = invocation.getArgument(0);
            ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
            return a;
        });

        AddressResponse response = addressService.createAddress(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.line1()).isEqualTo("123 Main St");
        assertThat(response.line2()).isEqualTo("Apt 4B");
        assertThat(response.city()).isEqualTo("New York");
        assertThat(response.region()).isEqualTo("NY");
        assertThat(response.postalCode()).isEqualTo("10001");
        assertThat(response.country()).isEqualTo("USA");
        assertThat(response.phone()).isEqualTo("+1234567890");
        assertThat(response.isDefault()).isTrue();

        verify(addressRepository, times(1)).save(any(Address.class));
    }

    @Test
    @DisplayName("createAddress - Success with required fields only and null isDefault")
    void createAddress_success_withRequiredFieldsOnly() {
        CreateAddressRequest request = new CreateAddressRequest(
            "456 Elm St",
            null,
            "Chicago",
            null,
            "60601",
            "USA",
            null,
            null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address a = invocation.getArgument(0);
            ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
            return a;
        });

        AddressResponse response = addressService.createAddress(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.line1()).isEqualTo("456 Elm St");
        assertThat(response.line2()).isNull();
        assertThat(response.region()).isNull();
        assertThat(response.phone()).isNull();
        assertThat(response.isDefault()).isFalse();

        verify(addressRepository, times(1)).save(any(Address.class));
    }

    @Test
    @DisplayName("createAddress - Throws UserNotFoundException when user does not exist")
    void createAddress_userNotFound_throwsException() {
        CreateAddressRequest request = new CreateAddressRequest(
            "123 Main St", null, "City", null, "12345", "Country", null, false
        );

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.createAddress(userId, request))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining(userId.toString());

        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAddressesByUserId - Returns addresses for user")
    void getAddressesByUserId_returnsList() {
        Address addr1 = new Address(testUser, "Line 1", null, "City1", null, "11111", "Country", null, true);
        ReflectionTestUtils.setField(addr1, "id", UUID.randomUUID());
        Address addr2 = new Address(testUser, "Line 2", null, "City2", null, "22222", "Country", null, false);
        ReflectionTestUtils.setField(addr2, "id", UUID.randomUUID());

        when(addressRepository.findAllByUserId(userId)).thenReturn(List.of(addr1, addr2));

        List<AddressResponse> responses = addressService.getAddressesByUserId(userId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).line1()).isEqualTo("Line 1");
        assertThat(responses.get(1).line1()).isEqualTo("Line 2");
    }

    @Test
    @DisplayName("getAddressesByUserId - Returns empty list when user has no addresses")
    void getAddressesByUserId_returnsEmptyList() {
        when(addressRepository.findAllByUserId(userId)).thenReturn(List.of());

        List<AddressResponse> responses = addressService.getAddressesByUserId(userId);

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("getAddressById - Success when address exists and caller owns it")
    void getAddressById_success() {
        UUID addressId = UUID.randomUUID();
        Address address = new Address(testUser, "Line 1", null, "City", null, "12345", "Country", null, false);
        ReflectionTestUtils.setField(address, "id", addressId);

        when(addressRepository.findById(addressId)).thenReturn(Optional.of(address));
        doNothing().when(ownershipGuard).checkOwnership(userId);

        AddressResponse response = addressService.getAddressById(addressId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(addressId);
        assertThat(response.userId()).isEqualTo(userId);
        verify(ownershipGuard, times(1)).checkOwnership(userId);
    }

    @Test
    @DisplayName("getAddressById - Throws AddressNotFoundException when address does not exist")
    void getAddressById_notFound_throwsException() {
        UUID addressId = UUID.randomUUID();
        when(addressRepository.findById(addressId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getAddressById(addressId))
            .isInstanceOf(AddressNotFoundException.class)
            .hasMessageContaining(addressId.toString());

        verify(ownershipGuard, never()).checkOwnership(any());
    }

    @Test
    @DisplayName("getAddressById - Throws OwnershipViolationException when caller does not own address")
    void getAddressById_ownershipViolation_throwsException() {
        UUID addressId = UUID.randomUUID();
        Address address = new Address(testUser, "Line 1", null, "City", null, "12345", "Country", null, false);
        ReflectionTestUtils.setField(address, "id", addressId);

        when(addressRepository.findById(addressId)).thenReturn(Optional.of(address));
        doThrow(new OwnershipViolationException("Access denied: you do not own this resource"))
            .when(ownershipGuard).checkOwnership(userId);

        assertThatThrownBy(() -> addressService.getAddressById(addressId))
            .isInstanceOf(OwnershipViolationException.class)
            .hasMessageContaining("you do not own this resource");
    }

    // ==========================================
    // updateAddress Tests
    // ==========================================

    @Test
    @DisplayName("updateAddress - Partial update updates only provided fields and preserves omitted fields")
    void updateAddress_success_partialUpdatePreservesOmittedFields() {
        UUID addressId = UUID.randomUUID();
        Address existing = new Address(
            testUser,
            "10 Main St",
            "Floor 2",
            "Cairo",
            "Cairo Governorate",
            "11111",
            "Egypt",
            "+1234567890",
            false
        );
        ReflectionTestUtils.setField(existing, "id", addressId);

        UpdateAddressRequest request = new UpdateAddressRequest(
            null,
            null,
            "Giza",
            null,
            null,
            null,
            null,
            null
        );

        when(addressRepository.findById(addressId)).thenReturn(Optional.of(existing));
        doNothing().when(ownershipGuard).checkOwnership(userId);
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.updateAddress(addressId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(addressId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.line1()).isEqualTo("10 Main St");
        assertThat(response.line2()).isEqualTo("Floor 2");
        assertThat(response.city()).isEqualTo("Giza"); // Updated
        assertThat(response.region()).isEqualTo("Cairo Governorate");
        assertThat(response.postalCode()).isEqualTo("11111");
        assertThat(response.country()).isEqualTo("Egypt");
        assertThat(response.phone()).isEqualTo("+1234567890");
        assertThat(response.isDefault()).isFalse();

        verify(ownershipGuard, times(1)).checkOwnership(userId);
        verify(addressRepository, times(1)).save(existing);
    }

    @Test
    @DisplayName("updateAddress - Updates all provided fields including isDefault")
    void updateAddress_success_updatesAllFields() {
        UUID addressId = UUID.randomUUID();
        Address existing = new Address(
            testUser,
            "Old Line 1",
            "Old Line 2",
            "Old City",
            "Old Region",
            "00000",
            "Old Country",
            "+0000000000",
            false
        );
        ReflectionTestUtils.setField(existing, "id", addressId);

        UpdateAddressRequest request = new UpdateAddressRequest(
            "New Line 1",
            "New Line 2",
            "New City",
            "New Region",
            "99999",
            "New Country",
            "+9999999999",
            true
        );

        when(addressRepository.findById(addressId)).thenReturn(Optional.of(existing));
        doNothing().when(ownershipGuard).checkOwnership(userId);
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.updateAddress(addressId, request);

        assertThat(response).isNotNull();
        assertThat(response.line1()).isEqualTo("New Line 1");
        assertThat(response.line2()).isEqualTo("New Line 2");
        assertThat(response.city()).isEqualTo("New City");
        assertThat(response.region()).isEqualTo("New Region");
        assertThat(response.postalCode()).isEqualTo("99999");
        assertThat(response.country()).isEqualTo("New Country");
        assertThat(response.phone()).isEqualTo("+9999999999");
        assertThat(response.isDefault()).isTrue();

        verify(ownershipGuard, times(1)).checkOwnership(userId);
        verify(addressRepository, times(1)).save(existing);
    }

    @Test
    @DisplayName("updateAddress - Throws AddressNotFoundException when address does not exist")
    void updateAddress_notFound_throwsException() {
        UUID addressId = UUID.randomUUID();
        UpdateAddressRequest request = new UpdateAddressRequest("New Line 1", null, null, null, null, null, null, null);

        when(addressRepository.findById(addressId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddress(addressId, request))
            .isInstanceOf(AddressNotFoundException.class)
            .hasMessageContaining(addressId.toString());

        verify(ownershipGuard, never()).checkOwnership(any());
        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateAddress - Throws OwnershipViolationException when caller does not own address")
    void updateAddress_ownershipViolation_throwsException() {
        UUID addressId = UUID.randomUUID();
        Address existing = new Address(testUser, "10 Main St", null, "Cairo", null, "11111", "Egypt", null, false);
        ReflectionTestUtils.setField(existing, "id", addressId);

        UpdateAddressRequest request = new UpdateAddressRequest("New Line 1", null, null, null, null, null, null, null);

        when(addressRepository.findById(addressId)).thenReturn(Optional.of(existing));
        doThrow(new OwnershipViolationException("Access denied: you do not own this resource"))
            .when(ownershipGuard).checkOwnership(userId);

        assertThatThrownBy(() -> addressService.updateAddress(addressId, request))
            .isInstanceOf(OwnershipViolationException.class)
            .hasMessageContaining("you do not own this resource");

        verify(addressRepository, never()).save(any());
    }

    // ==========================================
    // deleteAddress Tests
    // ==========================================

    @Test
    @DisplayName("deleteAddress - Success when caller owns the address")
    void deleteAddress_success() {
        UUID addressId = UUID.randomUUID();
        Address existing = new Address(testUser, "10 Main St", null, "Cairo", null, "11111", "Egypt", null, false);
        ReflectionTestUtils.setField(existing, "id", addressId);

        when(addressRepository.findById(addressId)).thenReturn(Optional.of(existing));
        doNothing().when(ownershipGuard).checkOwnership(userId);

        addressService.deleteAddress(addressId);

        verify(ownershipGuard, times(1)).checkOwnership(userId);
        verify(addressRepository, times(1)).delete(existing);
    }

    @Test
    @DisplayName("deleteAddress - Throws AddressNotFoundException when address does not exist")
    void deleteAddress_notFound_throwsException() {
        UUID addressId = UUID.randomUUID();

        when(addressRepository.findById(addressId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(addressId))
            .isInstanceOf(AddressNotFoundException.class)
            .hasMessageContaining(addressId.toString());

        verify(ownershipGuard, never()).checkOwnership(any());
        verify(addressRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteAddress - Throws OwnershipViolationException when caller does not own address")
    void deleteAddress_ownershipViolation_throwsException() {
        UUID addressId = UUID.randomUUID();
        Address existing = new Address(testUser, "10 Main St", null, "Cairo", null, "11111", "Egypt", null, false);
        ReflectionTestUtils.setField(existing, "id", addressId);

        when(addressRepository.findById(addressId)).thenReturn(Optional.of(existing));
        doThrow(new OwnershipViolationException("Access denied: you do not own this resource"))
            .when(ownershipGuard).checkOwnership(userId);

        assertThatThrownBy(() -> addressService.deleteAddress(addressId))
            .isInstanceOf(OwnershipViolationException.class)
            .hasMessageContaining("you do not own this resource");

        verify(addressRepository, never()).delete(any());
    }

    // ==========================================
    // Default Address Invariant Tests
    // ==========================================

    @Test
    @DisplayName("createAddress - With isDefault=true unsets existing default address")
    void createAddress_withDefaultTrue_unsetsExistingDefault() {
        UUID existingDefaultId = UUID.randomUUID();
        Address existingDefault = new Address(
            testUser, "Old Default St", null, "Cairo", null, "11111", "Egypt", null, true
        );
        ReflectionTestUtils.setField(existingDefault, "id", existingDefaultId);

        CreateAddressRequest request = new CreateAddressRequest(
            "New Default St", null, "Giza", null, "12345", "Egypt", null, true
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(addressRepository.findDefaultByUserId(userId)).thenReturn(Optional.of(existingDefault));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address a = invocation.getArgument(0);
            ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
            return a;
        });

        AddressResponse response = addressService.createAddress(userId, request);

        assertThat(response.isDefault()).isTrue();
        assertThat(existingDefault.isDefault()).isFalse();
        verify(addressRepository, times(1)).saveAndFlush(existingDefault);
        verify(addressRepository, times(1)).save(any(Address.class));
    }

    @Test
    @DisplayName("createAddress - With isDefault=false does not query or mutate existing default")
    void createAddress_withDefaultFalse_doesNotLookupOrUnsetDefault() {
        CreateAddressRequest request = new CreateAddressRequest(
            "Non Default St", null, "Cairo", null, "11111", "Egypt", null, false
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address a = invocation.getArgument(0);
            ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
            return a;
        });

        AddressResponse response = addressService.createAddress(userId, request);

        assertThat(response.isDefault()).isFalse();
        verify(addressRepository, never()).findDefaultByUserId(any());
        verify(addressRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("createAddress - With isDefault=true when no prior default persists as default")
    void createAddress_withDefaultTrue_noPriorDefault_persistsAsDefault() {
        CreateAddressRequest request = new CreateAddressRequest(
            "First Default St", null, "Cairo", null, "11111", "Egypt", null, true
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(addressRepository.findDefaultByUserId(userId)).thenReturn(Optional.empty());
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address a = invocation.getArgument(0);
            ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
            return a;
        });

        AddressResponse response = addressService.createAddress(userId, request);

        assertThat(response.isDefault()).isTrue();
        verify(addressRepository, times(1)).findDefaultByUserId(userId);
        verify(addressRepository, never()).saveAndFlush(any());
        verify(addressRepository, times(1)).save(any(Address.class));
    }

    @Test
    @DisplayName("updateAddress - Setting isDefault=true unsets other existing default address")
    void updateAddress_withDefaultTrue_unsetsOtherExistingDefault() {
        UUID addressId = UUID.randomUUID();
        Address addressToUpdate = new Address(
            testUser, "Address 2", null, "Cairo", null, "11111", "Egypt", null, false
        );
        ReflectionTestUtils.setField(addressToUpdate, "id", addressId);

        UUID previousDefaultId = UUID.randomUUID();
        Address previousDefault = new Address(
            testUser, "Address 1", null, "Cairo", null, "11111", "Egypt", null, true
        );
        ReflectionTestUtils.setField(previousDefault, "id", previousDefaultId);

        UpdateAddressRequest request = new UpdateAddressRequest(
            null, null, null, null, null, null, null, true
        );

        when(addressRepository.findById(addressId)).thenReturn(Optional.of(addressToUpdate));
        doNothing().when(ownershipGuard).checkOwnership(userId);
        when(addressRepository.findDefaultByUserId(userId)).thenReturn(Optional.of(previousDefault));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.updateAddress(addressId, request);

        assertThat(response.isDefault()).isTrue();
        assertThat(addressToUpdate.isDefault()).isTrue();
        assertThat(previousDefault.isDefault()).isFalse();
        verify(addressRepository, times(1)).saveAndFlush(previousDefault);
        verify(addressRepository, times(1)).save(addressToUpdate);
    }

    @Test
    @DisplayName("updateAddress - Setting isDefault=true when this address is already default does not unset itself")
    void updateAddress_withDefaultTrue_sameAddressAlreadyDefault_doesNotUnsetItself() {
        UUID addressId = UUID.randomUUID();
        Address existingDefault = new Address(
            testUser, "Address 1", null, "Cairo", null, "11111", "Egypt", null, true
        );
        ReflectionTestUtils.setField(existingDefault, "id", addressId);

        UpdateAddressRequest request = new UpdateAddressRequest(
            null, null, null, null, null, null, null, true
        );

        when(addressRepository.findById(addressId)).thenReturn(Optional.of(existingDefault));
        doNothing().when(ownershipGuard).checkOwnership(userId);
        when(addressRepository.findDefaultByUserId(userId)).thenReturn(Optional.of(existingDefault));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.updateAddress(addressId, request);

        assertThat(response.isDefault()).isTrue();
        assertThat(existingDefault.isDefault()).isTrue();
        verify(addressRepository, never()).saveAndFlush(any());
        verify(addressRepository, times(1)).save(existingDefault);
    }

    @Test
    @DisplayName("updateAddress - Setting isDefault=false leaves user with zero default addresses")
    void updateAddress_withDefaultFalse_leavesZeroDefaultAddresses() {
        UUID addressId = UUID.randomUUID();
        Address existingDefault = new Address(
            testUser, "Address 1", null, "Cairo", null, "11111", "Egypt", null, true
        );
        ReflectionTestUtils.setField(existingDefault, "id", addressId);

        UpdateAddressRequest request = new UpdateAddressRequest(
            null, null, null, null, null, null, null, false
        );

        when(addressRepository.findById(addressId)).thenReturn(Optional.of(existingDefault));
        doNothing().when(ownershipGuard).checkOwnership(userId);
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.updateAddress(addressId, request);

        assertThat(response.isDefault()).isFalse();
        assertThat(existingDefault.isDefault()).isFalse();
        verify(addressRepository, never()).findDefaultByUserId(any());
        verify(addressRepository, never()).saveAndFlush(any());
        verify(addressRepository, times(1)).save(existingDefault);
    }

    @Test
    @DisplayName("updateAddress - Omitted isDefault field preserves current default status")
    void updateAddress_omittedDefaultField_preservesCurrentDefaultStatus() {
        UUID addressId = UUID.randomUUID();
        Address existingDefault = new Address(
            testUser, "Address 1", null, "Cairo", null, "11111", "Egypt", null, true
        );
        ReflectionTestUtils.setField(existingDefault, "id", addressId);

        UpdateAddressRequest request = new UpdateAddressRequest(
            "New Line 1", null, null, null, null, null, null, null
        );

        when(addressRepository.findById(addressId)).thenReturn(Optional.of(existingDefault));
        doNothing().when(ownershipGuard).checkOwnership(userId);
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.updateAddress(addressId, request);

        assertThat(response.line1()).isEqualTo("New Line 1");
        assertThat(response.isDefault()).isTrue();
        assertThat(existingDefault.isDefault()).isTrue();
        verify(addressRepository, never()).findDefaultByUserId(any());
        verify(addressRepository, never()).saveAndFlush(any());
        verify(addressRepository, times(1)).save(existingDefault);
    }

    @Test
    @DisplayName("deleteAddress - Deleting default address cleanly deletes and leaves user with zero defaults")
    void deleteAddress_whenDefaultAddress_deletesCleanlyLeavingZeroDefaults() {
        UUID addressId = UUID.randomUUID();
        Address defaultAddress = new Address(
            testUser, "Default St", null, "Cairo", null, "11111", "Egypt", null, true
        );
        ReflectionTestUtils.setField(defaultAddress, "id", addressId);

        when(addressRepository.findById(addressId)).thenReturn(Optional.of(defaultAddress));
        doNothing().when(ownershipGuard).checkOwnership(userId);

        addressService.deleteAddress(addressId);

        verify(ownershipGuard, times(1)).checkOwnership(userId);
        verify(addressRepository, times(1)).delete(defaultAddress);
    }
}
