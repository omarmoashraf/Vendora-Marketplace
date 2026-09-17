package com.omar.vendora.sellers.service;

import com.omar.vendora.common.exception.SellerApplicationAlreadyDecidedException;
import com.omar.vendora.common.exception.SellerApplicationAlreadyPendingException;
import com.omar.vendora.common.exception.SellerApplicationNotFoundException;
import com.omar.vendora.common.exception.UserNotFoundException;
import com.omar.vendora.notifications.service.NotificationService;
import com.omar.vendora.sellers.domain.ApplicationStatus;
import com.omar.vendora.sellers.domain.SellerApplication;
import com.omar.vendora.sellers.domain.SellerProfile;
import com.omar.vendora.sellers.dto.SellerApplicationResponse;
import com.omar.vendora.sellers.dto.SubmitSellerApplicationRequest;
import com.omar.vendora.sellers.repository.SellerApplicationRepository;
import com.omar.vendora.sellers.repository.SellerProfileRepository;
import com.omar.vendora.users.domain.User;
import com.omar.vendora.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SellerApplicationServiceImpl implements SellerApplicationService {

    private final SellerApplicationRepository sellerApplicationRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public SellerApplicationServiceImpl(SellerApplicationRepository sellerApplicationRepository,
                                        SellerProfileRepository sellerProfileRepository,
                                        UserRepository userRepository,
                                        NotificationService notificationService) {
        this.sellerApplicationRepository = sellerApplicationRepository;
        this.sellerProfileRepository = sellerProfileRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public SellerApplicationResponse submitApplication(UUID userId, SubmitSellerApplicationRequest request) {
        if (sellerApplicationRepository.existsByUserIdAndStatus(userId, ApplicationStatus.PENDING)) {
            throw new SellerApplicationAlreadyPendingException(userId);
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        SellerApplication application = new SellerApplication(
            user,
            request.businessName(),
            request.notes()
        );

        try {
            SellerApplication savedApplication = sellerApplicationRepository.saveAndFlush(application);
            return SellerApplicationResponse.fromEntity(savedApplication);
        } catch (DataIntegrityViolationException ex) {
            throw new SellerApplicationAlreadyPendingException(userId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SellerApplicationResponse getMyApplication(UUID userId) {
        SellerApplication application = sellerApplicationRepository
            .findFirstByUserIdOrderByCreatedAtDesc(userId)
            .orElseThrow(() -> new SellerApplicationNotFoundException(userId));

        return SellerApplicationResponse.fromEntity(application);
    }

    @Override
    @Transactional
    public SellerApplicationResponse approveApplication(UUID applicationId, UUID adminId) {
        SellerApplication application = sellerApplicationRepository.findById(applicationId)
            .orElseThrow(() -> new SellerApplicationNotFoundException("Seller application with ID '" + applicationId + "' not found"));

        if (!application.isPending()) {
            throw new SellerApplicationAlreadyDecidedException(applicationId, application.getStatus());
        }

        User admin = userRepository.findById(adminId)
            .orElseThrow(() -> new UserNotFoundException(adminId));

        application.approve(admin);
        SellerApplication savedApplication = sellerApplicationRepository.save(application);

        User applicant = application.getUser();
        applicant.setSeller(true);
        userRepository.save(applicant);

        SellerProfile profile = new SellerProfile(applicant, application.getBusinessName());
        sellerProfileRepository.save(profile);

        notificationService.notify(
            applicant.getId(),
            "Seller Application Approved",
            "Congratulations! Your seller application for '" + application.getBusinessName() + "' has been approved."
        );

        return SellerApplicationResponse.fromEntity(savedApplication);
    }

    @Override
    @Transactional
    public SellerApplicationResponse rejectApplication(UUID applicationId, UUID adminId, String reason) {
        SellerApplication application = sellerApplicationRepository.findById(applicationId)
            .orElseThrow(() -> new SellerApplicationNotFoundException("Seller application with ID '" + applicationId + "' not found"));

        if (!application.isPending()) {
            throw new SellerApplicationAlreadyDecidedException(applicationId, application.getStatus());
        }

        User admin = userRepository.findById(adminId)
            .orElseThrow(() -> new UserNotFoundException(adminId));

        application.reject(admin);
        SellerApplication savedApplication = sellerApplicationRepository.save(application);

        String message = (reason != null && !reason.isBlank())
            ? "Your seller application for '" + application.getBusinessName() + "' was rejected. Reason: " + reason
            : "Your seller application for '" + application.getBusinessName() + "' was rejected.";

        notificationService.notify(
            application.getUser().getId(),
            "Seller Application Rejected",
            message
        );

        return SellerApplicationResponse.fromEntity(savedApplication);
    }
}
