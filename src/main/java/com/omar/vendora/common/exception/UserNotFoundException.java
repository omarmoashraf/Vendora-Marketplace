package com.omar.vendora.common.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("User with id '" + userId + "' not found");
    }

    public UserNotFoundException(String message) {
        super(message);
    }
}
