package com.quantumlock.audit;

import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.common.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller for audit log inspection and verification (ADR-006).
 *
 * <p>Endpoints: {@code POST /api/v1/audit/verify} and {@code GET /api/v1/audit/verify}
 * Accessible by ADMIN and AUDITOR roles. Scoped strictly to the caller's tenant.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit trail verification and integrity checks")
public class AuditController {

    private final AuditService auditService;

    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @Operation(summary = "Verify audit chain integrity (POST)",
            description = "Walks the append-only hash chain from genesis and reports whether it is intact or the first inconsistent event found.")
    public ResponseEntity<AuditService.VerificationResult> verifyAuditChainPost(
            @AuthenticationPrincipal AppUserDetails principal) {
        return performVerification();
    }

    @GetMapping("/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @Operation(summary = "Verify audit chain integrity (GET)",
            description = "Walks the append-only hash chain from genesis and reports whether it is intact or the first inconsistent event found.")
    public ResponseEntity<AuditService.VerificationResult> verifyAuditChainGet(
            @AuthenticationPrincipal AppUserDetails principal) {
        return performVerification();
    }

    private ResponseEntity<AuditService.VerificationResult> performVerification() {
        UUID tenantId = TenantContext.requireTenantId();
        AuditService.VerificationResult result = auditService.verifyChain(tenantId);
        return ResponseEntity.ok(result);
    }
}
