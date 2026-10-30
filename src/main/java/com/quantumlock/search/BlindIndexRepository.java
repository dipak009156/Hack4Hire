package com.quantumlock.search;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BlindIndexRepository extends JpaRepository<BlindIndex, UUID> {

    List<BlindIndex> findByTenantIdAndTokenHmac(UUID tenantId, String tokenHmac);

    List<BlindIndex> findByDocumentId(UUID documentId);

    void deleteAllByDocumentId(UUID documentId);
}
