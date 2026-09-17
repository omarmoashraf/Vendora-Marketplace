package com.omar.vendora.common.exception;

import java.util.UUID;

public class SellerNotFoundException extends RuntimeException {

    public SellerNotFoundException(UUID sellerId) {
        super("Seller profile with id '" + sellerId + "' not found");
    }

    public SellerNotFoundException(String message) {
        super(message);
    }
}
