package com.omar.vendora.users.service;

import com.omar.vendora.users.dto.UpdateProfileRequest;
import com.omar.vendora.users.dto.UserAuthDto;
import com.omar.vendora.users.dto.UserDto;

import java.util.Optional;
import java.util.UUID;

public interface UserService {

    boolean existsByEmail(String email);

    UserDto createUser(String email, String passwordHash, String fullName, String phone);

    UserDto getUserById(UUID id);

    UserDto updateProfile(UUID userId, UpdateProfileRequest request);

    Optional<UserAuthDto> findByEmailForAuth(String email);

    Optional<UserAuthDto> findByIdForAuth(UUID id);
}
