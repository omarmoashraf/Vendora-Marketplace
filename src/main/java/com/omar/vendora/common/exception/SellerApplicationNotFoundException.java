package com.omar.vendora.common.exception;

import java.util.UUID;

public class SellerApplicationNotFoundException extends RuntimeException {

    public SellerApplicationNotFoundException(UUID userId) {
        super("Seller application for user '" + userId + "' not found");
    }

    public SellerApplicationNotFoundException(String message) {
        super(message);
    }
}
