package com.quantumlock.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CredentialRequestRepository extends JpaRepository<CredentialRequest, UUID> {
    List<CredentialRequest> findAllByOrderByCreatedAtDesc();
    List<CredentialRequest> findByStatusOrderByCreatedAtDesc(String status);
    Optional<CredentialRequest> findByTenantNameIgnoreCaseAndStatus(String tenantName, String status);
}

