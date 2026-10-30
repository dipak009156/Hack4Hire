package com.quantumlock.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("SELECT a FROM AuditEvent a WHERE a.tenantId = :tenantId ORDER BY a.chainPosition DESC LIMIT 1")
    Optional<AuditEvent> findLatestByTenantId(@Param("tenantId") UUID tenantId);

    Optional<AuditEvent> findTopByTenantIdOrderByChainPositionDesc(UUID tenantId);

    List<AuditEvent> findAllByTenantIdOrderByChainPositionAsc(UUID tenantId);

    @Query("SELECT COALESCE(MAX(a.chainPosition), 0) FROM AuditEvent a WHERE a.tenantId = :tenantId")
    Long findMaxChainPosition(@Param("tenantId") UUID tenantId);
}
