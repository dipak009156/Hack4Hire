package com.quantumlock.sharing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID> {

    List<AccessGrant> findAllByDocumentIdAndTenantId(UUID documentId, UUID tenantId);

    Optional<AccessGrant> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AccessGrant> findAllByDocumentIdAndGranteeIdAndTenantIdOrderByCreatedAtDesc(
            UUID documentId, UUID granteeId, UUID tenantId);

    /** Active grants for a given grantee on a document (not revoked, not expired). */
    @Query("SELECT g FROM AccessGrant g WHERE g.documentId = :documentId " +
           "AND g.granteeId = :granteeId AND g.tenantId = :tenantId " +
           "AND g.revokedAt IS NULL " +
           "AND (g.expiresAt IS NULL OR g.expiresAt > :now) " +
           "AND (g.remainingDownloads IS NULL OR g.remainingDownloads > 0)")
    List<AccessGrant> findActiveGrantsForGrantee(
            @Param("documentId") UUID documentId,
            @Param("granteeId") UUID granteeId,
            @Param("tenantId") UUID tenantId,
            @Param("now") Instant now);

    /** Optimistic decrement of remaining_downloads — only if still > 0. */
    @Modifying
    @Query("UPDATE AccessGrant g SET g.remainingDownloads = g.remainingDownloads - 1 " +
           "WHERE g.id = :id AND g.remainingDownloads > 0")
    int decrementRemainingDownloads(@Param("id") UUID id);
}
