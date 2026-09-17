package com.omar.vendora.common.exception;

import java.util.UUID;

public class SellerApplicationAlreadyPendingException extends RuntimeException {

    public SellerApplicationAlreadyPendingException(UUID userId) {
        super("User '" + userId + "' already has a pending seller application");
    }
}
