package com.quantumlock.document;

import com.quantumlock.common.TenantAwareRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository extends TenantAwareRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findAllByDocumentIdAndTenantIdOrderByVersionDesc(UUID documentId, UUID tenantId);

    Optional<DocumentVersion> findByDocumentIdAndTenantIdAndVersion(UUID documentId, UUID tenantId, int version);

    Optional<DocumentVersion> findTopByDocumentIdAndTenantIdOrderByVersionDesc(UUID documentId, UUID tenantId);

    @Modifying
    @Query("UPDATE DocumentVersion dv SET dv.state = :newState WHERE dv.id = :id AND dv.tenantId = :tenantId AND dv.state = :expectedState")
    int compareAndSetState(@Param("id") UUID id,
                           @Param("tenantId") UUID tenantId,
                           @Param("expectedState") DocumentState expectedState,
                           @Param("newState") DocumentState newState);

    @Query("SELECT dv FROM DocumentVersion dv WHERE dv.state IN ('PENDING', 'OBJECT_STORED', 'RECOVERING') AND dv.createdAt < :cutoff")
    List<DocumentVersion> findStaleUploads(@Param("cutoff") Instant cutoff);

    /** Used by key rotation: fetch COMMITTED versions with old KEK, after cursor, paged. */
    @Query("SELECT dv FROM DocumentVersion dv WHERE dv.tenantId = :tenantId " +
           "AND dv.kekVersion = :kekVersion AND dv.state = 'COMMITTED' " +
           "AND (:cursor IS NULL OR dv.id > :cursor) ORDER BY dv.id ASC")
    List<DocumentVersion> findCommittedVersionsForRotation(
            @Param("tenantId") UUID tenantId,
            @Param("kekVersion") int kekVersion,
            @Param("cursor") UUID cursor,
            Pageable pageable);

    /** Count total versions to rotate — used to initialise progress tracking. */
    @Query("SELECT COUNT(dv) FROM DocumentVersion dv WHERE dv.tenantId = :tenantId " +
           "AND dv.kekVersion = :kekVersion AND dv.state = 'COMMITTED'")
    long countCommittedVersionsForRotation(
            @Param("tenantId") UUID tenantId,
            @Param("kekVersion") int kekVersion);
}
