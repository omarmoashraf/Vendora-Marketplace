package com.omar.vendora.sellers.service;

import com.omar.vendora.sellers.dto.SellerApplicationResponse;
import com.omar.vendora.sellers.dto.SubmitSellerApplicationRequest;

import java.util.UUID;

public interface SellerApplicationService {

    SellerApplicationResponse submitApplication(UUID userId, SubmitSellerApplicationRequest request);

    SellerApplicationResponse getMyApplication(UUID userId);

    SellerApplicationResponse approveApplication(UUID applicationId, UUID adminId);

    SellerApplicationResponse rejectApplication(UUID applicationId, UUID adminId, String reason);
}
