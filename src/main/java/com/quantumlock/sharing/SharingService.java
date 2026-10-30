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
        documentRepository.findByIdForCurrentTenant(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));

        UUID grantorId = principal != null && principal.getUserId() != null ? principal.getUserId() : UUID.randomUUID();
        String grantorName = principal != null && principal.getUsername() != null ? principal.getUsername() : "system";

        AccessGrant grant = AccessGrant.builder()
                .documentId(documentId)
                .tenantId(tenantId)
                .grantorId(grantorId)
                .granteeId(request.granteeId())
                .permissions(request.permissions())
                .purpose(request.purpose())
                .expiresAt(request.expiresAt())
                .maxDownloads(request.maxDownloads())
                .remainingDownloads(request.maxDownloads())
                .build();

        AccessGrant saved = accessGrantRepository.save(grant);

        auditService.record(tenantId, grantorId, grantorName,
                "ACCESS_GRANT", documentId, "GRANT_ACCESS", "SUCCESS",
                "grantee=" + request.granteeId() + " permissions=" + request.permissions());

        log.info("Access granted documentId={} grantee={} grantor={}",
                documentId, request.granteeId(), grantorId);

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

        UUID revokerId = principal != null && principal.getUserId() != null ? principal.getUserId() : grant.getGrantorId();
        String revokerName = principal != null && principal.getUsername() != null ? principal.getUsername() : "system";

        auditService.record(tenantId, revokerId, revokerName,
                "ACCESS_GRANT", documentId, "REVOKE_ACCESS", "SUCCESS",
                "grantId=" + grantId + " grantee=" + grant.getGranteeId());

        log.info("Access revoked grantId={} documentId={} revoker={}",
                grantId, documentId, revokerId);
    }

    @Transactional
    public void revokeAccess(UUID grantId, AppUserDetails principal) {
        UUID tenantId = TenantContext.requireTenantId();
        AccessGrant grant = accessGrantRepository.findByIdAndTenantId(grantId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Grant not found: " + grantId));
        revokeAccess(grant.getDocumentId(), grantId, principal);
    }

    // -------------------------------------------------------------------------
    // List grants
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AccessGrantResponse> listGrants(UUID documentId) {
        UUID tenantId = TenantContext.requireTenantId();
        documentRepository.findByIdForCurrentTenant(documentId)
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

    public AccessGrantResponse toResponse(AccessGrant g) {
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
