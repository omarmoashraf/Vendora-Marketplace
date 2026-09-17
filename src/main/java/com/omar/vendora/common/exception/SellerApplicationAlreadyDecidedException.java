package com.omar.vendora.common.exception;

import com.omar.vendora.sellers.domain.ApplicationStatus;

import java.util.UUID;

public class SellerApplicationAlreadyDecidedException extends IllegalStateException {

    public SellerApplicationAlreadyDecidedException(UUID id, ApplicationStatus status) {
        super("Cannot decide seller application '" + id + "' with status: " + status);
    }

    public SellerApplicationAlreadyDecidedException(String message) {
        super(message);
    }
}
