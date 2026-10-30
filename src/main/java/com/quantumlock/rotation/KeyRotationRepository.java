package com.quantumlock.rotation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KeyRotationRepository extends JpaRepository<KeyRotation, UUID> {

    Optional<KeyRotation> findByIdAndTenantId(UUID id, UUID tenantId);

    List<KeyRotation> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("SELECT r FROM KeyRotation r WHERE r.tenantId = :tenantId AND r.status IN ('PENDING','IN_PROGRESS')")
    List<KeyRotation> findActiveByTenantId(@Param("tenantId") UUID tenantId);
}

