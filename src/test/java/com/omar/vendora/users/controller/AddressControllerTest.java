package com.omar.vendora.users.controller;

import com.omar.vendora.identity.repository.RefreshTokenRepository;
import com.omar.vendora.security.JwtService;
import com.omar.vendora.users.domain.Address;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.domain.UserStatus;
import com.omar.vendora.users.repository.AddressRepository;
import com.omar.vendora.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        addressRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createAndPersistUser(String email, String fullName, String phone, boolean isCustomer, boolean isSeller, boolean isAdmin) {
        User user = new User(email, "hashed_pw_secret_123", fullName, phone);
        user.setStatus(UserStatus.ACTIVE);
        user.setCustomer(isCustomer);
        user.setSeller(isSeller);
        user.setAdmin(isAdmin);
        return userRepository.save(user);
    }

    // ==========================================
    // POST /users/me/addresses Tests
    // ==========================================

    @Test
    @DisplayName("POST /users/me/addresses - Happy path with required fields only returns 201 Created")
    void createAddress_happyPath_requiredFieldsOnly() throws Exception {
        User user = createAndPersistUser("customer1@example.com", "Customer One", "+1234567890", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "line1": "123 Main Street",
                "city": "Metropolis",
                "postalCode": "12345",
                "country": "Egypt"
            }
            """;

        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.userId").value(user.getId().toString()))
            .andExpect(jsonPath("$.line1").value("123 Main Street"))
            .andExpect(jsonPath("$.line2").doesNotExist())
            .andExpect(jsonPath("$.city").value("Metropolis"))
            .andExpect(jsonPath("$.region").doesNotExist())
            .andExpect(jsonPath("$.postalCode").value("12345"))
            .andExpect(jsonPath("$.country").value("Egypt"))
            .andExpect(jsonPath("$.phone").doesNotExist())
            .andExpect(jsonPath("$.isDefault").value(false));

        List<Address> saved = addressRepository.findAllByUserId(user.getId());
        assertThat(saved).hasSize(1);
        Address address = saved.get(0);
        assertThat(address.getLine1()).isEqualTo("123 Main Street");
        assertThat(address.getUser().getId()).isEqualTo(user.getId());
        assertThat(address.isDefault()).isFalse();
    }

    @Test
    @DisplayName("POST /users/me/addresses - Happy path with all optional fields returns 201 Created")
    void createAddress_happyPath_allFields() throws Exception {
        User user = createAndPersistUser("customer2@example.com", "Customer Two", "+1234567890", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "line1": "456 Market Ave",
                "line2": "Suite 300",
                "city": "Cairo",
                "region": "Cairo Governorate",
                "postalCode": "11511",
                "country": "Egypt",
                "phone": "+201012345678",
                "isDefault": true
            }
            """;

        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.userId").value(user.getId().toString()))
            .andExpect(jsonPath("$.line1").value("456 Market Ave"))
            .andExpect(jsonPath("$.line2").value("Suite 300"))
            .andExpect(jsonPath("$.city").value("Cairo"))
            .andExpect(jsonPath("$.region").value("Cairo Governorate"))
            .andExpect(jsonPath("$.postalCode").value("11511"))
            .andExpect(jsonPath("$.country").value("Egypt"))
            .andExpect(jsonPath("$.phone").value("+201012345678"))
            .andExpect(jsonPath("$.isDefault").value(true));

        List<Address> saved = addressRepository.findAllByUserId(user.getId());
        assertThat(saved).hasSize(1);
        Address address = saved.get(0);
        assertThat(address.getLine1()).isEqualTo("456 Market Ave");
        assertThat(address.getLine2()).isEqualTo("Suite 300");
        assertThat(address.getCity()).isEqualTo("Cairo");
        assertThat(address.getRegion()).isEqualTo("Cairo Governorate");
        assertThat(address.getPostalCode()).isEqualTo("11511");
        assertThat(address.getCountry()).isEqualTo("Egypt");
        assertThat(address.getPhone()).isEqualTo("+201012345678");
        assertThat(address.isDefault()).isTrue();
    }

    @Test
    @DisplayName("POST /users/me/addresses - Accepts snake_case alias fields (postal_code, is_default)")
    void createAddress_snakeCaseAliasSupport() throws Exception {
        User user = createAndPersistUser("alias@example.com", "Alias User", "+1234567890", true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "line1": "789 Alias Blvd",
                "city": "Alexandria",
                "postal_code": "21500",
                "country": "Egypt",
                "is_default": true
            }
            """;

        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.postalCode").value("21500"))
            .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    @DisplayName("POST /users/me/addresses - Unauthenticated request returns 401 UNAUTHORIZED")
    void createAddress_unauthenticated_returns401() throws Exception {
        String requestBody = """
            {
                "line1": "123 Main St",
                "city": "Cairo",
                "postalCode": "12345",
                "country": "Egypt"
            }
            """;

        mockMvc.perform(post("/users/me/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /users/me/addresses - Non-customer role (e.g. ROLE_SELLER only) returns 403 FORBIDDEN")
    void createAddress_nonCustomerRole_returns403() throws Exception {
        User seller = createAndPersistUser("seller_only@example.com", "Seller Only", null, false, true, false);
        String token = jwtService.generateToken(seller.getId(), seller.getEmail(), List.of("ROLE_SELLER"));

        String requestBody = """
            {
                "line1": "123 Seller St",
                "city": "Cairo",
                "postalCode": "12345",
                "country": "Egypt"
            }
            """;

        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /users/me/addresses - Validation failure: missing line1 returns 400")
    void createAddress_missingLine1_returns400() throws Exception {
        User user = createAndPersistUser("val1@example.com", "Val User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "city": "Cairo",
                "postalCode": "12345",
                "country": "Egypt"
            }
            """;

        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /users/me/addresses - Validation failure: missing city returns 400")
    void createAddress_missingCity_returns400() throws Exception {
        User user = createAndPersistUser("val2@example.com", "Val User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "line1": "123 Main St",
                "postalCode": "12345",
                "country": "Egypt"
            }
            """;

        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /users/me/addresses - Validation failure: missing postalCode returns 400")
    void createAddress_missingPostalCode_returns400() throws Exception {
        User user = createAndPersistUser("val3@example.com", "Val User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "line1": "123 Main St",
                "city": "Cairo",
                "country": "Egypt"
            }
            """;

        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /users/me/addresses - Validation failure: missing country returns 400")
    void createAddress_missingCountry_returns400() throws Exception {
        User user = createAndPersistUser("val4@example.com", "Val User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "line1": "123 Main St",
                "city": "Cairo",
                "postalCode": "12345"
            }
            """;

        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /users/me/addresses - Validation failure: line1 exceeds 255 chars returns 400")
    void createAddress_line1TooLong_returns400() throws Exception {
        User user = createAndPersistUser("val5@example.com", "Val User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "line1": "%s",
                "city": "Cairo",
                "postalCode": "12345",
                "country": "Egypt"
            }
            """.formatted("a".repeat(256));

        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /users/me/addresses - Validation failure: postalCode exceeds 20 chars returns 400")
    void createAddress_postalCodeTooLong_returns400() throws Exception {
        User user = createAndPersistUser("val6@example.com", "Val User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "line1": "123 Main St",
                "city": "Cairo",
                "postalCode": "%s",
                "country": "Egypt"
            }
            """.formatted("1".repeat(21));

        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /users/me/addresses - Validation failure: phone exceeds 50 chars returns 400")
    void createAddress_phoneTooLong_returns400() throws Exception {
        User user = createAndPersistUser("val7@example.com", "Val User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "line1": "123 Main St",
                "city": "Cairo",
                "postalCode": "12345",
                "country": "Egypt",
                "phone": "%s"
            }
            """.formatted("1".repeat(51));

        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /users/me/addresses - Identity isolation: foreign userId in request body is ignored and address belongs to authenticated user")
    void createAddress_ignoresForeignUserId() throws Exception {
        User userA = createAndPersistUser("userA@example.com", "User Alpha", null, true, false, false);
        User userB = createAndPersistUser("userB@example.com", "User Beta", null, true, false, false);

        String tokenA = jwtService.generateToken(userA.getId(), userA.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "userId": "%s",
                "line1": "Attempted Spoof St",
                "city": "Cairo",
                "postalCode": "12345",
                "country": "Egypt"
            }
            """.formatted(userB.getId());

        mockMvc.perform(post("/users/me/addresses")
                .param("userId", userB.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(userA.getId().toString()));

        // Confirm User B has 0 addresses and User A has 1 address
        assertThat(addressRepository.findAllByUserId(userB.getId())).isEmpty();
        assertThat(addressRepository.findAllByUserId(userA.getId())).hasSize(1);
    }

    // ==========================================
    // GET /users/me/addresses Tests
    // ==========================================

    @Test
    @DisplayName("GET /users/me/addresses - Returns empty list when user has no addresses")
    void getAddresses_emptyList() throws Exception {
        User user = createAndPersistUser("empty@example.com", "Empty User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        mockMvc.perform(get("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /users/me/addresses - Returns list of owned addresses")
    void getAddresses_returnsOwnedAddresses() throws Exception {
        User user = createAndPersistUser("multi@example.com", "Multi User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        Address addr1 = new Address(user, "Addr 1", null, "City 1", null, "11111", "Egypt", null, true);
        Address addr2 = new Address(user, "Addr 2", "Apt 2", "City 2", "Reg 2", "22222", "Egypt", "+123", false);
        addressRepository.saveAll(List.of(addr1, addr2));

        List<Address> dbList = addressRepository.findAllByUserId(user.getId());

        mockMvc.perform(get("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(dbList.get(0).getId().toString()))
            .andExpect(jsonPath("$[0].line1").value(dbList.get(0).getLine1()))
            .andExpect(jsonPath("$[0].isDefault").value(dbList.get(0).isDefault()))
            .andExpect(jsonPath("$[1].id").value(dbList.get(1).getId().toString()))
            .andExpect(jsonPath("$[1].line1").value(dbList.get(1).getLine1()))
            .andExpect(jsonPath("$[1].isDefault").value(dbList.get(1).isDefault()));
    }

    @Test
    @DisplayName("GET /users/me/addresses - Cross-user isolation: User A only sees User A's addresses")
    void getAddresses_crossUserIsolation() throws Exception {
        User userA = createAndPersistUser("userA_list@example.com", "User Alpha", null, true, false, false);
        User userB = createAndPersistUser("userB_list@example.com", "User Beta", null, true, false, false);

        Address addrA = new Address(userA, "Addr of A", null, "Cairo", null, "11111", "Egypt", null, true);
        Address addrB = new Address(userB, "Addr of B", null, "Giza", null, "22222", "Egypt", null, true);
        addressRepository.saveAll(List.of(addrA, addrB));

        String tokenA = jwtService.generateToken(userA.getId(), userA.getEmail(), List.of("ROLE_CUSTOMER"));
        String tokenB = jwtService.generateToken(userB.getId(), userB.getEmail(), List.of("ROLE_CUSTOMER"));

        // User A only sees Addr of A
        mockMvc.perform(get("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].line1").value("Addr of A"))
            .andExpect(jsonPath("$[0].userId").value(userA.getId().toString()));

        // User B only sees Addr of B
        mockMvc.perform(get("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].line1").value("Addr of B"))
            .andExpect(jsonPath("$[0].userId").value(userB.getId().toString()));
    }

    @Test
    @DisplayName("GET /users/me/addresses - Unauthenticated request returns 401 UNAUTHORIZED")
    void getAddresses_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/users/me/addresses"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /users/me/addresses - Non-customer role returns 403 FORBIDDEN")
    void getAddresses_nonCustomerRole_returns403() throws Exception {
        User seller = createAndPersistUser("seller_list@example.com", "Seller Only", null, false, true, false);
        String token = jwtService.generateToken(seller.getId(), seller.getEmail(), List.of("ROLE_SELLER"));

        mockMvc.perform(get("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ==========================================
    // GET /users/me/addresses/{id} Tests
    // ==========================================

    @Test
    @DisplayName("GET /users/me/addresses/{id} - Happy path: owner retrieves own address (200 OK)")
    void getAddressById_happyPath_returns200() throws Exception {
        User user = createAndPersistUser("owner@example.com", "Owner User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        Address address = addressRepository.save(
            new Address(user, "123 Owned Way", "Floor 2", "Alexandria", "Alex", "21500", "Egypt", "+20123", true)
        );

        mockMvc.perform(get("/users/me/addresses/" + address.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(address.getId().toString()))
            .andExpect(jsonPath("$.userId").value(user.getId().toString()))
            .andExpect(jsonPath("$.line1").value("123 Owned Way"))
            .andExpect(jsonPath("$.line2").value("Floor 2"))
            .andExpect(jsonPath("$.city").value("Alexandria"))
            .andExpect(jsonPath("$.region").value("Alex"))
            .andExpect(jsonPath("$.postalCode").value("21500"))
            .andExpect(jsonPath("$.country").value("Egypt"))
            .andExpect(jsonPath("$.phone").value("+20123"))
            .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    @DisplayName("GET /users/me/addresses/{id} - IDOR Protection: User A attempting to read User B's address returns 403 FORBIDDEN")
    void getAddressById_crossUserAccess_returns403Forbidden() throws Exception {
        User userA = createAndPersistUser("userA_idor@example.com", "User Alpha", null, true, false, false);
        User userB = createAndPersistUser("userB_idor@example.com", "User Beta", null, true, false, false);

        Address addressB = addressRepository.save(
            new Address(userB, "Secret Manor of B", null, "London", null, "SW1A 1AA", "UK", null, false)
        );

        String tokenA = jwtService.generateToken(userA.getId(), userA.getEmail(), List.of("ROLE_CUSTOMER"));

        // User A attempts to access Address of User B
        mockMvc.perform(get("/users/me/addresses/" + addressB.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.error.message").value("Access denied: you do not own this resource"));
    }

    @Test
    @DisplayName("GET /users/me/addresses/{id} - Non-existent address ID returns 404 ADDRESS_NOT_FOUND")
    void getAddressById_notFound_returns404() throws Exception {
        User user = createAndPersistUser("seeker@example.com", "Seeker User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/users/me/addresses/" + nonExistentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("ADDRESS_NOT_FOUND"))
            .andExpect(jsonPath("$.error.message").value("Address with id '" + nonExistentId + "' not found"));
    }

    @Test
    @DisplayName("GET /users/me/addresses/{id} - Unauthenticated request returns 401 UNAUTHORIZED")
    void getAddressById_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/users/me/addresses/" + UUID.randomUUID()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /users/me/addresses/{id} - Non-customer role returns 403 FORBIDDEN")
    void getAddressById_nonCustomerRole_returns403() throws Exception {
        User seller = createAndPersistUser("seller_single@example.com", "Seller Only", null, false, true, false);
        String token = jwtService.generateToken(seller.getId(), seller.getEmail(), List.of("ROLE_SELLER"));

        mockMvc.perform(get("/users/me/addresses/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ==========================================
    // PATCH /users/me/addresses/{id} Tests
    // ==========================================

    @Test
    @DisplayName("PATCH /users/me/addresses/{id} - Happy path: partial update updates single field and preserves others")
    void patchAddress_happyPath_updatesSingleField() throws Exception {
        User user = createAndPersistUser("patch_single@example.com", "Patch User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        Address address = addressRepository.save(
            new Address(user, "10 Main St", "Floor 1", "Cairo", "Cairo Gov", "11111", "Egypt", "+12345", false)
        );

        String requestBody = """
            {
                "city": "Giza"
            }
            """;

        mockMvc.perform(patch("/users/me/addresses/" + address.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(address.getId().toString()))
            .andExpect(jsonPath("$.userId").value(user.getId().toString()))
            .andExpect(jsonPath("$.line1").value("10 Main St"))
            .andExpect(jsonPath("$.line2").value("Floor 1"))
            .andExpect(jsonPath("$.city").value("Giza"))
            .andExpect(jsonPath("$.region").value("Cairo Gov"))
            .andExpect(jsonPath("$.postalCode").value("11111"))
            .andExpect(jsonPath("$.country").value("Egypt"))
            .andExpect(jsonPath("$.phone").value("+12345"))
            .andExpect(jsonPath("$.isDefault").value(false));

        Address updated = addressRepository.findById(address.getId()).orElseThrow();
        assertThat(updated.getCity()).isEqualTo("Giza");
        assertThat(updated.getLine1()).isEqualTo("10 Main St");
        assertThat(updated.getCountry()).isEqualTo("Egypt");
    }

    @Test
    @DisplayName("PATCH /users/me/addresses/{id} - Happy path: updates all fields including isDefault")
    void patchAddress_happyPath_updatesMultipleFields() throws Exception {
        User user = createAndPersistUser("patch_all@example.com", "Patch All", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        Address address = addressRepository.save(
            new Address(user, "Old St", "Old Suite", "Old City", "Old Reg", "00000", "Old Country", "+0000", false)
        );

        String requestBody = """
            {
                "line1": "New St",
                "line2": "New Suite",
                "city": "New City",
                "region": "New Reg",
                "postalCode": "99999",
                "country": "New Country",
                "phone": "+9999",
                "isDefault": true
            }
            """;

        mockMvc.perform(patch("/users/me/addresses/" + address.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(address.getId().toString()))
            .andExpect(jsonPath("$.line1").value("New St"))
            .andExpect(jsonPath("$.line2").value("New Suite"))
            .andExpect(jsonPath("$.city").value("New City"))
            .andExpect(jsonPath("$.region").value("New Reg"))
            .andExpect(jsonPath("$.postalCode").value("99999"))
            .andExpect(jsonPath("$.country").value("New Country"))
            .andExpect(jsonPath("$.phone").value("+9999"))
            .andExpect(jsonPath("$.isDefault").value(true));

        Address updated = addressRepository.findById(address.getId()).orElseThrow();
        assertThat(updated.getLine1()).isEqualTo("New St");
        assertThat(updated.getLine2()).isEqualTo("New Suite");
        assertThat(updated.getCity()).isEqualTo("New City");
        assertThat(updated.getRegion()).isEqualTo("New Reg");
        assertThat(updated.getPostalCode()).isEqualTo("99999");
        assertThat(updated.getCountry()).isEqualTo("New Country");
        assertThat(updated.getPhone()).isEqualTo("+9999");
        assertThat(updated.isDefault()).isTrue();
    }

    @Test
    @DisplayName("PATCH /users/me/addresses/{id} - Snake_case aliases are bound correctly")
    void patchAddress_happyPath_snakeCaseAliases() throws Exception {
        User user = createAndPersistUser("patch_alias@example.com", "Alias User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        Address address = addressRepository.save(
            new Address(user, "123 Alias Way", null, "Alexandria", null, "11111", "Egypt", null, false)
        );

        String requestBody = """
            {
                "postal_code": "21500",
                "is_default": true
            }
            """;

        mockMvc.perform(patch("/users/me/addresses/" + address.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.postalCode").value("21500"))
            .andExpect(jsonPath("$.isDefault").value(true));

        Address updated = addressRepository.findById(address.getId()).orElseThrow();
        assertThat(updated.getPostalCode()).isEqualTo("21500");
        assertThat(updated.isDefault()).isTrue();
    }

    @Test
    @DisplayName("PATCH /users/me/addresses/{id} - Omitted fields: empty body leaves address unchanged")
    void patchAddress_omittedFields_remainUnchanged() throws Exception {
        User user = createAndPersistUser("patch_empty@example.com", "Empty Body User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        Address address = addressRepository.save(
            new Address(user, "123 Preserved Way", "Apt 5", "Cairo", "Cairo Gov", "12345", "Egypt", "+123", true)
        );

        mockMvc.perform(patch("/users/me/addresses/" + address.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.line1").value("123 Preserved Way"))
            .andExpect(jsonPath("$.line2").value("Apt 5"))
            .andExpect(jsonPath("$.city").value("Cairo"))
            .andExpect(jsonPath("$.region").value("Cairo Gov"))
            .andExpect(jsonPath("$.postalCode").value("12345"))
            .andExpect(jsonPath("$.country").value("Egypt"))
            .andExpect(jsonPath("$.phone").value("+123"))
            .andExpect(jsonPath("$.isDefault").value(true));

        Address updated = addressRepository.findById(address.getId()).orElseThrow();
        assertThat(updated.getLine1()).isEqualTo("123 Preserved Way");
        assertThat(updated.getCity()).isEqualTo("Cairo");
    }

    @Test
    @DisplayName("PATCH /users/me/addresses/{id} - IDOR Protection: User A cannot update User B's address (403 FORBIDDEN)")
    void patchAddress_ownershipViolation_crossUserAccessRejected() throws Exception {
        User userA = createAndPersistUser("userA_patch@example.com", "User Alpha", null, true, false, false);
        User userB = createAndPersistUser("userB_patch@example.com", "User Beta", null, true, false, false);

        Address addressB = addressRepository.save(
            new Address(userB, "Target Line 1", null, "Target City", null, "54321", "UK", null, false)
        );

        String tokenA = jwtService.generateToken(userA.getId(), userA.getEmail(), List.of("ROLE_CUSTOMER"));

        String requestBody = """
            {
                "line1": "Hacked Line 1",
                "city": "Hacked City"
            }
            """;

        mockMvc.perform(patch("/users/me/addresses/" + addressB.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.error.message").value("Access denied: you do not own this resource"));

        // Verify User B's address remains untouched in DB
        Address untouched = addressRepository.findById(addressB.getId()).orElseThrow();
        assertThat(untouched.getLine1()).isEqualTo("Target Line 1");
        assertThat(untouched.getCity()).isEqualTo("Target City");
    }

    @Test
    @DisplayName("PATCH /users/me/addresses/{id} - Non-existent address ID returns 404 ADDRESS_NOT_FOUND")
    void patchAddress_notFound_returns404() throws Exception {
        User user = createAndPersistUser("patch_404@example.com", "Seeker User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        UUID nonExistentId = UUID.randomUUID();
        String requestBody = """
            {
                "city": "Nowhere"
            }
            """;

        mockMvc.perform(patch("/users/me/addresses/" + nonExistentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("ADDRESS_NOT_FOUND"))
            .andExpect(jsonPath("$.error.message").value("Address with id '" + nonExistentId + "' not found"));
    }

    @Test
    @DisplayName("PATCH /users/me/addresses/{id} - Unauthenticated request returns 401 UNAUTHORIZED")
    void patchAddress_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/users/me/addresses/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"city\": \"Nowhere\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PATCH /users/me/addresses/{id} - Non-customer role returns 403 FORBIDDEN")
    void patchAddress_nonCustomerRole_returns403() throws Exception {
        User seller = createAndPersistUser("seller_patch@example.com", "Seller Only", null, false, true, false);
        String token = jwtService.generateToken(seller.getId(), seller.getEmail(), List.of("ROLE_SELLER"));

        mockMvc.perform(patch("/users/me/addresses/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"city\": \"Nowhere\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PATCH /users/me/addresses/{id} - Validation failure: field length exceeding max returns 400 VALIDATION_ERROR")
    void patchAddress_validationFailure_fieldTooLong() throws Exception {
        User user = createAndPersistUser("patch_val@example.com", "Val User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        Address address = addressRepository.save(
            new Address(user, "123 Valid St", null, "Valid City", null, "12345", "Egypt", null, false)
        );

        String longLine1 = "a".repeat(256);
        String requestBody = """
            {
                "line1": "%s"
            }
            """.formatted(longLine1);

        mockMvc.perform(patch("/users/me/addresses/" + address.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("Validation failed"))
            .andExpect(jsonPath("$.error.details[0].field").value("line1"));
    }

    @Test
    @DisplayName("PATCH /users/me/addresses/{id} - Mass assignment immunity: protected fields (id, user) cannot be changed")
    void patchAddress_massAssignmentImmunity_ignoresProtectedFields() throws Exception {
        User user = createAndPersistUser("patch_mass@example.com", "Mass User", null, true, false, false);
        User attacker = createAndPersistUser("attacker@example.com", "Attacker", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        Address address = addressRepository.save(
            new Address(user, "Safe St", null, "Safe City", null, "12345", "Egypt", null, false)
        );

        UUID forgedId = UUID.randomUUID();
        String requestBody = """
            {
                "id": "%s",
                "userId": "%s",
                "user_id": "%s",
                "city": "Updated City"
            }
            """.formatted(forgedId, attacker.getId(), attacker.getId());

        mockMvc.perform(patch("/users/me/addresses/" + address.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(address.getId().toString()))
            .andExpect(jsonPath("$.userId").value(user.getId().toString()))
            .andExpect(jsonPath("$.city").value("Updated City"));

        Address reloaded = addressRepository.findById(address.getId()).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(address.getId());
        assertThat(reloaded.getUser().getId()).isEqualTo(user.getId());
        assertThat(reloaded.getCity()).isEqualTo("Updated City");
    }

    // ==========================================
    // DELETE /users/me/addresses/{id} Tests
    // ==========================================

    @Test
    @DisplayName("DELETE /users/me/addresses/{id} - Happy path: owner deletes own address returns 204 NO_CONTENT")
    void deleteAddress_happyPath_returns204AndDeletesFromDb() throws Exception {
        User user = createAndPersistUser("delete_owner@example.com", "Delete Owner", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        Address address = addressRepository.save(
            new Address(user, "To Delete St", null, "Delete City", null, "12345", "Egypt", null, false)
        );
        UUID addressId = address.getId();

        mockMvc.perform(delete("/users/me/addresses/" + addressId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        assertThat(addressRepository.findById(addressId)).isEmpty();
    }

    @Test
    @DisplayName("DELETE /users/me/addresses/{id} - IDOR Protection: User A cannot delete User B's address (403 FORBIDDEN)")
    void deleteAddress_ownershipViolation_crossUserAccessRejected() throws Exception {
        User userA = createAndPersistUser("userA_del@example.com", "User Alpha", null, true, false, false);
        User userB = createAndPersistUser("userB_del@example.com", "User Beta", null, true, false, false);

        Address addressB = addressRepository.save(
            new Address(userB, "Target St", null, "Target City", null, "12345", "Egypt", null, false)
        );
        UUID addressIdB = addressB.getId();

        String tokenA = jwtService.generateToken(userA.getId(), userA.getEmail(), List.of("ROLE_CUSTOMER"));

        mockMvc.perform(delete("/users/me/addresses/" + addressIdB)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.error.message").value("Access denied: you do not own this resource"));

        // Verify User B's address still exists in DB
        assertThat(addressRepository.findById(addressIdB)).isPresent();
    }

    @Test
    @DisplayName("DELETE /users/me/addresses/{id} - Non-existent address ID returns 404 ADDRESS_NOT_FOUND")
    void deleteAddress_notFound_returns404() throws Exception {
        User user = createAndPersistUser("delete_404@example.com", "Seeker User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(delete("/users/me/addresses/" + nonExistentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("ADDRESS_NOT_FOUND"))
            .andExpect(jsonPath("$.error.message").value("Address with id '" + nonExistentId + "' not found"));
    }

    @Test
    @DisplayName("DELETE /users/me/addresses/{id} - Unauthenticated request returns 401 UNAUTHORIZED")
    void deleteAddress_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/users/me/addresses/" + UUID.randomUUID()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("DELETE /users/me/addresses/{id} - Non-customer role returns 403 FORBIDDEN")
    void deleteAddress_nonCustomerRole_returns403() throws Exception {
        User seller = createAndPersistUser("seller_del@example.com", "Seller Only", null, false, true, false);
        String token = jwtService.generateToken(seller.getId(), seller.getEmail(), List.of("ROLE_SELLER"));

        mockMvc.perform(delete("/users/me/addresses/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ==========================================
    // Default Address Invariant End-to-End Tests
    // ==========================================

    @Test
    @DisplayName("Invariant: POST /users/me/addresses with isDefault=true unsets previous default address")
    void createAddress_whenAlreadyHasDefault_unsetsOldDefaultAndPromotesNew() throws Exception {
        User user = createAndPersistUser("create_invariant@example.com", "Invariant User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        // Step 1: Create Address 1 as default
        String req1 = """
            {
                "line1": "Address 1",
                "city": "Cairo",
                "postalCode": "11111",
                "country": "Egypt",
                "isDefault": true
            }
            """;
        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(req1))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isDefault").value(true));

        // Step 2: Create Address 2 as default
        String req2 = """
            {
                "line1": "Address 2",
                "city": "Giza",
                "postalCode": "22222",
                "country": "Egypt",
                "isDefault": true
            }
            """;
        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(req2))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isDefault").value(true));

        // Verify in DB that exactly one address is default (Address 2 is true, Address 1 is false)
        List<Address> addresses = addressRepository.findAllByUserId(user.getId());
        assertThat(addresses).hasSize(2);

        Address addr1 = addresses.stream().filter(a -> a.getLine1().equals("Address 1")).findFirst().orElseThrow();
        Address addr2 = addresses.stream().filter(a -> a.getLine1().equals("Address 2")).findFirst().orElseThrow();

        assertThat(addr1.isDefault()).isFalse();
        assertThat(addr2.isDefault()).isTrue();
    }

    @Test
    @DisplayName("Invariant: POST /users/me/addresses with isDefault=false preserves existing default address")
    void createAddress_whenHasDefault_creatingNonDefault_preservesExistingDefault() throws Exception {
        User user = createAndPersistUser("create_nondefault@example.com", "NonDefault User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        // Address 1 as default
        Address addr1 = addressRepository.save(
            new Address(user, "Default St", null, "Cairo", null, "11111", "Egypt", null, true)
        );

        // Create Address 2 with isDefault = false
        String req = """
            {
                "line1": "Non-Default St",
                "city": "Giza",
                "postalCode": "22222",
                "country": "Egypt",
                "isDefault": false
            }
            """;
        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(req))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isDefault").value(false));

        Address reloadedAddr1 = addressRepository.findById(addr1.getId()).orElseThrow();
        assertThat(reloadedAddr1.isDefault()).isTrue();
    }

    @Test
    @DisplayName("Invariant: PATCH /users/me/addresses/{id} with isDefault=true unsets previous default address")
    void patchAddress_settingToDefault_unsetsPreviousDefault() throws Exception {
        User user = createAndPersistUser("patch_invariant@example.com", "Patch Invariant User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        Address addr1 = addressRepository.save(
            new Address(user, "Addr 1 Default", null, "Cairo", null, "11111", "Egypt", null, true)
        );
        Address addr2 = addressRepository.save(
            new Address(user, "Addr 2 Non-Default", null, "Alexandria", null, "22222", "Egypt", null, false)
        );

        // Update Address 2 to default
        mockMvc.perform(patch("/users/me/addresses/" + addr2.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isDefault\": true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isDefault").value(true));

        Address reloaded1 = addressRepository.findById(addr1.getId()).orElseThrow();
        Address reloaded2 = addressRepository.findById(addr2.getId()).orElseThrow();

        assertThat(reloaded1.isDefault()).isFalse();
        assertThat(reloaded2.isDefault()).isTrue();
    }

    @Test
    @DisplayName("Invariant: PATCH /users/me/addresses/{id} with isDefault=false allows user to have zero defaults")
    void patchAddress_unsettingDefault_leavesZeroDefaults() throws Exception {
        User user = createAndPersistUser("patch_unsetting@example.com", "Unset User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        Address addr1 = addressRepository.save(
            new Address(user, "Only Default", null, "Cairo", null, "11111", "Egypt", null, true)
        );

        mockMvc.perform(patch("/users/me/addresses/" + addr1.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isDefault\": false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isDefault").value(false));

        Address reloaded = addressRepository.findById(addr1.getId()).orElseThrow();
        assertThat(reloaded.isDefault()).isFalse();
        assertThat(addressRepository.findDefaultByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("Invariant: DELETE default address leaves user with zero defaults and no auto-promotion")
    void deleteAddress_whenDefaultDeleted_leavesZeroDefaultsWithoutAutoPromotion() throws Exception {
        User user = createAndPersistUser("delete_invariant@example.com", "Del Invariant User", null, true, false, false);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("ROLE_CUSTOMER"));

        Address addr1 = addressRepository.save(
            new Address(user, "Default St", null, "Cairo", null, "11111", "Egypt", null, true)
        );
        Address addr2 = addressRepository.save(
            new Address(user, "Other St", null, "Giza", null, "22222", "Egypt", null, false)
        );

        mockMvc.perform(delete("/users/me/addresses/" + addr1.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isNoContent());

        assertThat(addressRepository.findById(addr1.getId())).isEmpty();
        Address reloadedAddr2 = addressRepository.findById(addr2.getId()).orElseThrow();
        assertThat(reloadedAddr2.isDefault()).isFalse();
        assertThat(addressRepository.findDefaultByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("Invariant: Cross-user default setting does not touch other user default address")
    void crossUser_defaultOperationsAreStrictlyIsolated() throws Exception {
        User userA = createAndPersistUser("userA_inv@example.com", "User Alpha", null, true, false, false);
        User userB = createAndPersistUser("userB_inv@example.com", "User Beta", null, true, false, false);

        Address addrA = addressRepository.save(
            new Address(userA, "Addr of A", null, "Cairo", null, "11111", "Egypt", null, true)
        );

        String tokenB = jwtService.generateToken(userB.getId(), userB.getEmail(), List.of("ROLE_CUSTOMER"));

        // User B creates a default address
        String reqB = """
            {
                "line1": "Addr of B",
                "city": "Alexandria",
                "postalCode": "21500",
                "country": "Egypt",
                "isDefault": true
            }
            """;
        mockMvc.perform(post("/users/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reqB))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isDefault").value(true));

        // Confirm User A default address is untouched and remains true
        Address reloadedA = addressRepository.findById(addrA.getId()).orElseThrow();
        assertThat(reloadedA.isDefault()).isTrue();

        // Confirm User B has their default address
        assertThat(addressRepository.findDefaultByUserId(userB.getId())).isPresent();
    }
}
