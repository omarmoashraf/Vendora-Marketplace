package com.omar.vendora.users.service;

import com.omar.vendora.common.exception.EmailAlreadyExistsException;
import com.omar.vendora.common.exception.UserNotFoundException;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.domain.UserStatus;
import com.omar.vendora.users.dto.UpdateProfileRequest;
import com.omar.vendora.users.dto.UserDto;
import com.omar.vendora.users.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("getUserById - when user exists, returns UserDto with mapped fields")
    void getUserById_whenUserExists_returnsUserDto() {
        UUID userId = UUID.randomUUID();
        User user = new User("alice@example.com", "hashed_password", "Alice Smith", "+1234567890");
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserDto result = userService.getUserById(userId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.fullName()).isEqualTo("Alice Smith");
        assertThat(result.phone()).isEqualTo("+1234567890");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.isCustomer()).isTrue();
        assertThat(result.isSeller()).isFalse();
        assertThat(result.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("getUserById - when user does not exist, throws UserNotFoundException")
    void getUserById_whenUserNotFound_throwsUserNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(userId))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining(userId.toString());
    }

    @Test
    @DisplayName("createUser - when email does not exist, saves and returns UserDto")
    void createUser_success() {
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UserDto result = userService.createUser("bob@example.com", "hash", "Bob Jones", null);

        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("bob@example.com");
        assertThat(result.fullName()).isEqualTo("Bob Jones");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("createUser - when email exists, throws EmailAlreadyExistsException")
    void createUser_duplicateEmail_throwsException() {
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("existing@example.com", "hash", "Existing", null))
            .isInstanceOf(EmailAlreadyExistsException.class)
            .hasMessageContaining("existing@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile - when user exists and both fields provided, updates both")
    void updateProfile_whenBothFieldsProvided_updatesBoth() {
        UUID userId = UUID.randomUUID();
        User user = new User("alice@example.com", "hashed_password", "Original Name", "+1111111111");
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest("Updated Name", "+2222222222");
        UserDto result = userService.updateProfile(userId, request);

        assertThat(result).isNotNull();
        assertThat(result.fullName()).isEqualTo("Updated Name");
        assertThat(result.phone()).isEqualTo("+2222222222");
        assertThat(user.getFullName()).isEqualTo("Updated Name");
        assertThat(user.getPhone()).isEqualTo("+2222222222");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateProfile - when only fullName provided, phone remains unchanged")
    void updateProfile_whenOnlyFullNameProvided_phoneRemainsUnchanged() {
        UUID userId = UUID.randomUUID();
        User user = new User("alice@example.com", "hashed_password", "Original Name", "+1111111111");
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest("Updated Name Only", null);
        UserDto result = userService.updateProfile(userId, request);

        assertThat(result).isNotNull();
        assertThat(result.fullName()).isEqualTo("Updated Name Only");
        assertThat(result.phone()).isEqualTo("+1111111111");
        assertThat(user.getFullName()).isEqualTo("Updated Name Only");
        assertThat(user.getPhone()).isEqualTo("+1111111111");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateProfile - when only phone provided, fullName remains unchanged")
    void updateProfile_whenOnlyPhoneProvided_fullNameRemainsUnchanged() {
        UUID userId = UUID.randomUUID();
        User user = new User("alice@example.com", "hashed_password", "Original Name", "+1111111111");
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest(null, "+9999999999");
        UserDto result = userService.updateProfile(userId, request);

        assertThat(result).isNotNull();
        assertThat(result.fullName()).isEqualTo("Original Name");
        assertThat(result.phone()).isEqualTo("+9999999999");
        assertThat(user.getFullName()).isEqualTo("Original Name");
        assertThat(user.getPhone()).isEqualTo("+9999999999");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateProfile - when user does not exist, throws UserNotFoundException")
    void updateProfile_whenUserNotFound_throwsUserNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest("New Name", "+1234567890");

        assertThatThrownBy(() -> userService.updateProfile(userId, request))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining(userId.toString());

        verify(userRepository, never()).save(any());
    }
}
