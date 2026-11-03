package com.quantumlock.search;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface BlindIndexRepository extends JpaRepository<BlindIndex, UUID> {

    List<BlindIndex> findByTenantIdAndTokenHmac(UUID tenantId, String tokenHmac);

    List<BlindIndex> findAllByTenantIdAndTokenHmac(UUID tenantId, String tokenHmac);

    List<BlindIndex> findByDocumentId(UUID documentId);

    @Query("SELECT b.documentId FROM BlindIndex b WHERE b.tenantId = :tenantId AND b.tokenHmac IN :tokenHmacs")
    List<UUID> findDocumentIdsByTenantIdAndTokenHmacIn(
            @Param("tenantId") UUID tenantId,
            @Param("tokenHmacs") Collection<String> tokenHmacs);

    void deleteAllByDocumentId(UUID documentId);
}
