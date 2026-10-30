package com.quantumlock.document;

import com.quantumlock.common.TenantAwareRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DocumentRepository extends TenantAwareRepository<Document, UUID> {

    boolean existsByNameAndTenantId(String name, UUID tenantId);

    @Modifying
    @Query("UPDATE Document d SET d.currentVersion = :version, d.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE d.id = :id AND d.tenantId = :tenantId")
    int updateCurrentVersion(@Param("id") UUID id,
                             @Param("tenantId") UUID tenantId,
                             @Param("version") int version);
}

