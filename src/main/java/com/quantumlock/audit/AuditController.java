package com.quantumlock.audit;

import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.common.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller for audit log inspection and verification (ADR-006).
 *
 * <p>Endpoint: {@code POST /api/v1/audit/verify}
 * Accessible by ADMIN and AUDITOR roles. Scoped strictly to the caller's tenant.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit trail verification and integrity checks")
public class AuditController {

    private final AuditService auditService;

    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'USER')")
    @Operation(summary = "Verify audit chain integrity",
            description = "Walks the append-only hash chain from genesis and reports whether it is intact or the first inconsistent event found.")
    public ResponseEntity<AuditService.VerificationResult> verifyAuditChain(
            @AuthenticationPrincipal AppUserDetails principal) {

        UUID tenantId = TenantContext.requireTenantId();
        AuditService.VerificationResult result = auditService.verifyChain(tenantId);
        return ResponseEntity.ok(result);
    }

    @org.springframework.web.bind.annotation.GetMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'USER')")
    @Operation(summary = "Get audit chain events",
            description = "Returns chronological list of hash-chained audit events for the caller's tenant.")
    public ResponseEntity<java.util.List<AuditEvent>> getAuditEvents(
            @AuthenticationPrincipal AppUserDetails principal) {

        UUID tenantId = TenantContext.requireTenantId();
        return ResponseEntity.ok(auditService.getEvents(tenantId));
    }
}
