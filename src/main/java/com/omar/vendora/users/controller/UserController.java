package com.omar.vendora.users.controller;

import com.omar.vendora.security.CurrentUserProvider;
import com.omar.vendora.users.dto.UpdateProfileRequest;
import com.omar.vendora.users.dto.UserDto;
import com.omar.vendora.users.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    public UserController(UserService userService, CurrentUserProvider currentUserProvider) {
        this.userService = userService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> getCurrentUser() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        UserDto user = userService.getUserById(currentUserId);
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> updateCurrentUserProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        UserDto updatedUser = userService.updateProfile(currentUserId, request);
        return ResponseEntity.ok(updatedUser);
    }
}
