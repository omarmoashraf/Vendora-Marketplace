package com.omar.vendora.sellers.service;

import com.omar.vendora.sellers.dto.SellerProfileResponse;

import java.util.UUID;

public interface SellerProfileService {

    SellerProfileResponse suspendSeller(UUID sellerProfileId);

    SellerProfileResponse reactivateSeller(UUID sellerProfileId);
}
