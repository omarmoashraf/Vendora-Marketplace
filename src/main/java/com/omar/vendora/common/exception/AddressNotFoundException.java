package com.omar.vendora.common.exception;

import java.util.UUID;

public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException(UUID addressId) {
        super("Address with id '" + addressId + "' not found");
    }

    public AddressNotFoundException(String message) {
        super(message);
    }
}
