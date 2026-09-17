package com.omar.vendora.sellers.service;

import com.omar.vendora.common.exception.SellerNotFoundException;
import com.omar.vendora.sellers.domain.SellerProfile;
import com.omar.vendora.sellers.dto.SellerProfileResponse;
import com.omar.vendora.sellers.repository.SellerProfileRepository;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SellerProfileServiceImpl implements SellerProfileService {

    private final SellerProfileRepository sellerProfileRepository;
    private final UserRepository userRepository;

    public SellerProfileServiceImpl(SellerProfileRepository sellerProfileRepository,
                                    UserRepository userRepository) {
        this.sellerProfileRepository = sellerProfileRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public SellerProfileResponse suspendSeller(UUID sellerProfileId) {
        SellerProfile profile = sellerProfileRepository.findById(sellerProfileId)
            .orElseThrow(() -> new SellerNotFoundException(sellerProfileId));

        profile.suspend();
        SellerProfile savedProfile = sellerProfileRepository.save(profile);

        User user = profile.getUser();
        if (user != null) {
            user.setSeller(false);
            userRepository.save(user);
        }

        return SellerProfileResponse.fromEntity(savedProfile);
    }

    @Override
    @Transactional
    public SellerProfileResponse reactivateSeller(UUID sellerProfileId) {
        SellerProfile profile = sellerProfileRepository.findById(sellerProfileId)
            .orElseThrow(() -> new SellerNotFoundException(sellerProfileId));

        profile.reactivate();
        SellerProfile savedProfile = sellerProfileRepository.save(profile);

        User user = profile.getUser();
        if (user != null) {
            user.setSeller(true);
            userRepository.save(user);
        }

        return SellerProfileResponse.fromEntity(savedProfile);
    }
}
