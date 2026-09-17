package com.omar.vendora.users.repository;

import com.omar.vendora.identity.repository.RefreshTokenRepository;
import com.omar.vendora.users.domain.Address;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.domain.UserStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AddressRepositoryTest {

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        addressRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createAndPersistUser(String email) {
        User user = new User(email, "hashed_pw_secret_123", "John Doe", "+1234567890");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("save - Happy path: persists Address with all fields and associated User")
    void save_happyPath_persistsAddressWithUser() {
        User user = createAndPersistUser("address_user@example.com");

        Address address = new Address(
            user,
            "123 Market St",
            "Suite 400",
            "San Francisco",
            "CA",
            "94105",
            "USA",
            "+14155552671",
            false
        );

        Address saved = addressRepository.saveAndFlush(address);
        entityManager.clear();

        assertThat(saved.getId()).isNotNull();

        Optional<Address> foundOpt = addressRepository.findById(saved.getId());
        assertThat(foundOpt).isPresent();

        Address found = foundOpt.get();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getLine1()).isEqualTo("123 Market St");
        assertThat(found.getLine2()).isEqualTo("Suite 400");
        assertThat(found.getCity()).isEqualTo("San Francisco");
        assertThat(found.getRegion()).isEqualTo("CA");
        assertThat(found.getPostalCode()).isEqualTo("94105");
        assertThat(found.getCountry()).isEqualTo("USA");
        assertThat(found.getPhone()).isEqualTo("+14155552671");
        assertThat(found.isDefault()).isFalse();
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("save - Default values: isDefault is false by default")
    void save_defaultValues_isDefaultFalseByDefault() {
        User user = createAndPersistUser("default_test@example.com");

        Address address = new Address();
        address.setUser(user);
        address.setLine1("456 Elm St");
        address.setCity("Seattle");
        address.setPostalCode("98101");
        address.setCountry("USA");

        Address saved = addressRepository.saveAndFlush(address);
        entityManager.clear();

        Address found = addressRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.isDefault()).isFalse();
    }

    @Test
    @DisplayName("save - Custom flag: isDefault true persists correctly")
    void save_withDefaultTrue_persistsDefaultTrue() {
        User user = createAndPersistUser("default_true@example.com");

        Address address = new Address(
            user,
            "789 Oak St",
            null,
            "Austin",
            "TX",
            "78701",
            "USA",
            null,
            true
        );

        Address saved = addressRepository.saveAndFlush(address);
        entityManager.clear();

        Address found = addressRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.isDefault()).isTrue();
        assertThat(found.getLine2()).isNull();
        assertThat(found.getPhone()).isNull();
    }

    @Test
    @DisplayName("save - Null line1 violates non-null constraint")
    void save_nullLine1_throwsDataIntegrityViolationException() {
        User user = createAndPersistUser("null_line1@example.com");

        Address address = new Address(
            user,
            null,
            null,
            "Chicago",
            "IL",
            "60601",
            "USA",
            null,
            false
        );

        assertThatThrownBy(() -> addressRepository.saveAndFlush(address))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("save - Null city violates non-null constraint")
    void save_nullCity_throwsDataIntegrityViolationException() {
        User user = createAndPersistUser("null_city@example.com");

        Address address = new Address(
            user,
            "100 State St",
            null,
            null,
            "IL",
            "60601",
            "USA",
            null,
            false
        );

        assertThatThrownBy(() -> addressRepository.saveAndFlush(address))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("save - Null postalCode violates non-null constraint")
    void save_nullPostalCode_throwsDataIntegrityViolationException() {
        User user = createAndPersistUser("null_postal@example.com");

        Address address = new Address(
            user,
            "100 State St",
            null,
            "Chicago",
            "IL",
            null,
            "USA",
            null,
            false
        );

        assertThatThrownBy(() -> addressRepository.saveAndFlush(address))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("save - Null country violates non-null constraint")
    void save_nullCountry_throwsDataIntegrityViolationException() {
        User user = createAndPersistUser("null_country@example.com");

        Address address = new Address(
            user,
            "100 State St",
            null,
            "Chicago",
            "IL",
            "60601",
            null,
            null,
            false
        );

        assertThatThrownBy(() -> addressRepository.saveAndFlush(address))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("save - Null user violates foreign key / non-null constraint")
    void save_nullUser_throwsDataIntegrityViolationException() {
        Address address = new Address(
            null,
            "100 State St",
            null,
            "Chicago",
            "IL",
            "60601",
            "USA",
            null,
            false
        );

        assertThatThrownBy(() -> addressRepository.saveAndFlush(address))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Foreign key cascade - Deleting User cascades and removes associated Address")
    void deleteUser_cascadesAndDeletesAddresses() {
        User user = createAndPersistUser("cascade_del@example.com");

        Address address = new Address(
            user,
            "300 Pine St",
            null,
            "Denver",
            "CO",
            "80202",
            "USA",
            null,
            false
        );
        Address savedAddress = addressRepository.saveAndFlush(address);
        UUID addressId = savedAddress.getId();

        entityManager.clear();

        // Delete the user via repository
        userRepository.deleteById(user.getId());
        userRepository.flush();
        entityManager.clear();

        // The address should no longer exist
        assertThat(addressRepository.findById(addressId)).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Orphan removal - Removing Address from User's collection deletes Address")
    void orphanRemoval_removingAddressFromUser_deletesAddress() {
        User user = createAndPersistUser("orphan_removal@example.com");

        Address address = new Address(
            user,
            "500 Broadway",
            "Apt 2B",
            "New York",
            "NY",
            "10012",
            "USA",
            null,
            false
        );
        user.addAddress(address);
        User savedUser = userRepository.saveAndFlush(user);
        UUID addressId = savedUser.getAddresses().getFirst().getId();

        entityManager.flush();
        entityManager.clear();

        // Reload user, remove address from list, save and flush
        User loadedUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(loadedUser.getAddresses()).hasSize(1);

        Address toRemove = loadedUser.getAddresses().getFirst();
        loadedUser.removeAddress(toRemove);
        userRepository.saveAndFlush(loadedUser);
        entityManager.flush();
        entityManager.clear();

        // Address entity should be removed from database via orphanRemoval = true
        assertThat(addressRepository.findById(addressId)).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Lazy loading - Address.user is lazily fetched")
    void lazyLoading_userRelationshipIsLazy() {
        User user = createAndPersistUser("lazy_load@example.com");

        Address address = new Address(
            user,
            "200 Main St",
            null,
            "Boston",
            "MA",
            "02108",
            "USA",
            null,
            false
        );
        Address saved = addressRepository.saveAndFlush(address);
        entityManager.flush();
        entityManager.clear();

        Address loaded = addressRepository.findById(saved.getId()).orElseThrow();

        // User proxy should not be initialized yet
        assertThat(Hibernate.isInitialized(loaded.getUser())).isFalse();

        // Accessing user property initializes it
        assertThat(loaded.getUser().getEmail()).isEqualTo("lazy_load@example.com");
        assertThat(Hibernate.isInitialized(loaded.getUser())).isTrue();
    }

    // ==========================================
    // Default Address Invariant & Unique Index Tests
    // ==========================================

    @Test
    @DisplayName("findDefaultByUserId - Returns default address when one exists")
    void findDefaultByUserId_whenDefaultExists_returnsDefaultAddress() {
        User user = createAndPersistUser("default_query_user@example.com");

        Address addr1 = new Address(user, "Addr 1", null, "City 1", null, "11111", "Egypt", null, false);
        Address defaultAddr = new Address(user, "Addr 2", null, "City 2", null, "22222", "Egypt", null, true);
        addressRepository.saveAndFlush(addr1);
        Address savedDefault = addressRepository.saveAndFlush(defaultAddr);
        entityManager.clear();

        Optional<Address> found = addressRepository.findDefaultByUserId(user.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedDefault.getId());
        assertThat(found.get().isDefault()).isTrue();
    }

    @Test
    @DisplayName("findDefaultByUserId - Returns empty when user has only non-default addresses")
    void findDefaultByUserId_whenNoDefaultExists_returnsEmpty() {
        User user = createAndPersistUser("no_default_user@example.com");

        Address addr1 = new Address(user, "Addr 1", null, "City 1", null, "11111", "Egypt", null, false);
        addressRepository.saveAndFlush(addr1);
        entityManager.clear();

        Optional<Address> found = addressRepository.findDefaultByUserId(user.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findDefaultByUserId - Strictly scoped: returns only the queried user's default address")
    void findDefaultByUserId_strictlyScopedToQueriedUser() {
        User userA = createAndPersistUser("userA_scoped@example.com");
        User userB = createAndPersistUser("userB_scoped@example.com");

        Address addrA = new Address(userA, "Addr A", null, "Cairo", null, "11111", "Egypt", null, true);
        Address addrB = new Address(userB, "Addr B", null, "Alexandria", null, "22222", "Egypt", null, true);
        Address savedA = addressRepository.saveAndFlush(addrA);
        Address savedB = addressRepository.saveAndFlush(addrB);
        entityManager.clear();

        Optional<Address> foundA = addressRepository.findDefaultByUserId(userA.getId());
        Optional<Address> foundB = addressRepository.findDefaultByUserId(userB.getId());

        assertThat(foundA).isPresent();
        assertThat(foundA.get().getId()).isEqualTo(savedA.getId());
        assertThat(foundA.get().getUser().getId()).isEqualTo(userA.getId());

        assertThat(foundB).isPresent();
        assertThat(foundB.get().getId()).isEqualTo(savedB.getId());
        assertThat(foundB.get().getUser().getId()).isEqualTo(userB.getId());
    }

    @Test
    @DisplayName("Database Constraint - Inserting two default addresses for same user directly violates partial unique index")
    void databaseConstraint_twoDefaultAddressesForSameUser_throwsDataIntegrityViolationException() {
        User user = createAndPersistUser("double_default@example.com");

        Address addr1 = new Address(user, "Addr 1", null, "Cairo", null, "11111", "Egypt", null, true);
        addressRepository.saveAndFlush(addr1);

        Address addr2 = new Address(user, "Addr 2", null, "Cairo", null, "22222", "Egypt", null, true);

        assertThatThrownBy(() -> addressRepository.saveAndFlush(addr2))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Database Constraint - Multiple non-default addresses for same user are allowed")
    void databaseConstraint_multipleNonDefaultAddressesForSameUser_succeeds() {
        User user = createAndPersistUser("multiple_non_default@example.com");

        Address addr1 = new Address(user, "Addr 1", null, "Cairo", null, "11111", "Egypt", null, false);
        Address addr2 = new Address(user, "Addr 2", null, "Cairo", null, "22222", "Egypt", null, false);
        Address addr3 = new Address(user, "Addr 3", null, "Cairo", null, "33333", "Egypt", null, false);

        addressRepository.saveAndFlush(addr1);
        addressRepository.saveAndFlush(addr2);
        addressRepository.saveAndFlush(addr3);
        entityManager.clear();

        assertThat(addressRepository.findAllByUserId(user.getId())).hasSize(3);
    }

    @Test
    @DisplayName("Database Constraint - Different users can each have one default address concurrently")
    void databaseConstraint_differentUsersCanEachHaveDefaultAddress_succeeds() {
        User userA = createAndPersistUser("userA_concurrent_default@example.com");
        User userB = createAndPersistUser("userB_concurrent_default@example.com");

        Address addrA = new Address(userA, "Addr A", null, "Cairo", null, "11111", "Egypt", null, true);
        Address addrB = new Address(userB, "Addr B", null, "Giza", null, "22222", "Egypt", null, true);

        addressRepository.saveAndFlush(addrA);
        addressRepository.saveAndFlush(addrB);
        entityManager.clear();

        assertThat(addressRepository.findDefaultByUserId(userA.getId())).isPresent();
        assertThat(addressRepository.findDefaultByUserId(userB.getId())).isPresent();
    }
}
