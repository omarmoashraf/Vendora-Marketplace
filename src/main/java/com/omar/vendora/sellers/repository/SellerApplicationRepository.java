package com.omar.vendora.sellers.repository;

import com.omar.vendora.sellers.domain.ApplicationStatus;
import com.omar.vendora.sellers.domain.SellerApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerApplicationRepository extends JpaRepository<SellerApplication, UUID> {

    boolean existsByUserIdAndStatus(UUID userId, ApplicationStatus status);

    Optional<SellerApplication> findByUserIdAndStatus(UUID userId, ApplicationStatus status);

    Optional<SellerApplication> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SellerApplication> findAllByUserId(UUID userId);

    List<SellerApplication> findAllByStatus(ApplicationStatus status);
}
