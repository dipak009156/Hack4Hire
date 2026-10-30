package com.quantumlock.sharing;

import com.quantumlock.audit.AuditService;
import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.common.TenantContext;
import com.quantumlock.document.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages document access grants (share / revoke) with tenant isolation,
 * optimistic concurrency (row_version), and full audit trail (ADR-003, ADR-006).
 *
 * <h3>Grant lifecycle</h3>
 * <ol>
 *   <li>Owner calls {@link #grantAccess} — creates an active grant row.</li>
 *   <li>Access is checked with {@link #hasActiveGrant} before download.</li>
 *   <li>Owner or ADMIN calls {@link #revokeAccess} — sets {@code revoked_at}.</li>
 * </ol>
 *
 * <p>Idempotency-Key enforcement is handled at the controller layer.
 * Time-limited shares enforce {@code expires_at} on every access check.
 * Download-quota shares enforce {@code remaining_downloads > 0} via an atomic
 * {@code UPDATE ... WHERE remaining_downloads > 0} to avoid race conditions.
 */
@Service
@RequiredArgsConstructor
public class SharingService {

    private static final Logger log = LoggerFactory.getLogger(SharingService.class);

    private final AccessGrantRepository accessGrantRepository;
    private final DocumentRepository documentRepository;
    private final AuditService auditService;

    // -------------------------------------------------------------------------
    // Grant access
    // -------------------------------------------------------------------------

    @Transactional
    public AccessGrantResponse grantAccess(UUID documentId, ShareDocumentRequest request,
                                           AppUserDetails principal) {
        UUID tenantId = TenantContext.requireTenantId();

        // Verify document belongs to this tenant and the principal owns or manages it
        documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));

        AccessGrant grant = AccessGrant.builder()
                .documentId(documentId)
                .tenantId(tenantId)
                .grantorId(principal.getUserId())
                .granteeId(request.granteeId())
                .permissions(request.permissions())
                .purpose(request.purpose())
                .expiresAt(request.expiresAt())
                .maxDownloads(request.maxDownloads())
                .remainingDownloads(request.maxDownloads())
                .build();

        AccessGrant saved = accessGrantRepository.save(grant);

        auditService.record(tenantId, principal.getUserId(), principal.getUsername(),
                "ACCESS_GRANT", documentId, "GRANT_ACCESS", "SUCCESS",
                "grantee=" + request.granteeId() + " permissions=" + request.permissions());

        log.info("Access granted documentId={} grantee={} grantor={}",
                documentId, request.granteeId(), principal.getUserId());

        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Revoke access
    // -------------------------------------------------------------------------

    @Transactional
    public void revokeAccess(UUID documentId, UUID grantId, AppUserDetails principal) {
        UUID tenantId = TenantContext.requireTenantId();

        AccessGrant grant = accessGrantRepository.findByIdAndTenantId(grantId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Grant not found: " + grantId));

        if (!grant.getDocumentId().equals(documentId)) {
            throw new IllegalArgumentException("Grant does not belong to document " + documentId);
        }

        if (grant.getRevokedAt() != null) {
            log.info("Grant {} already revoked at {}", grantId, grant.getRevokedAt());
            return; // idempotent
        }

        grant.setRevokedAt(Instant.now());
        accessGrantRepository.save(grant);

        auditService.record(tenantId, principal.getUserId(), principal.getUsername(),
                "ACCESS_GRANT", documentId, "REVOKE_ACCESS", "SUCCESS",
                "grantId=" + grantId + " grantee=" + grant.getGranteeId());

        log.info("Access revoked grantId={} documentId={} revoker={}",
                grantId, documentId, principal.getUserId());
    }

    // -------------------------------------------------------------------------
    // List grants
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AccessGrantResponse> listAllGrants() {
        UUID tenantId = TenantContext.requireTenantId();
        return accessGrantRepository.findAllByTenantId(tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccessGrantResponse> listGrantsCreatedBy(UUID grantorId) {
        UUID tenantId = TenantContext.requireTenantId();
        return accessGrantRepository.findAllByGrantorIdAndTenantId(grantorId, tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccessGrantResponse> listGrantsSharedWith(UUID granteeId) {
        UUID tenantId = TenantContext.requireTenantId();
        return accessGrantRepository.findAllByGranteeIdAndTenantId(granteeId, tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccessGrantResponse> listGrants(UUID documentId) {
        UUID tenantId = TenantContext.requireTenantId();
        documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));

        return accessGrantRepository.findAllByDocumentIdAndTenantId(documentId, tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Check access (used by download/decrypt endpoints)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public boolean hasActiveGrant(UUID documentId, UUID granteeId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<AccessGrant> active = accessGrantRepository
                .findActiveGrantsForGrantee(documentId, granteeId, tenantId, Instant.now());
        return !active.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Mapper
    // -------------------------------------------------------------------------

    private AccessGrantResponse toResponse(AccessGrant g) {
        return new AccessGrantResponse(
                g.getId(),
                g.getDocumentId(),
                g.getGrantorId(),
                g.getGranteeId(),
                g.getPermissions(),
                g.getPurpose(),
                g.getExpiresAt(),
                g.getMaxDownloads(),
                g.getRemainingDownloads(),
                g.isActive(),
                g.getRevokedAt(),
                g.getCreatedAt()
        );
    }
}

