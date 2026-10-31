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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for audit log inspection and verification (ADR-006).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/audit/verify} — verify chain integrity</li>
 *   <li>{@code GET  /api/v1/audit/events} — paginated/filtered event list</li>
 * </ul>
 * Accessible by ADMIN, AUDITOR, USER roles. Scoped strictly to the caller's tenant.
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

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'USER')")
    @Operation(summary = "Get audit chain events",
            description = "Returns chronological list of hash-chained audit events for the caller's tenant. "
                    + "Optional query params: page (0-based), size (default=all), action (exact match filter).")
    public ResponseEntity<List<AuditEvent>> getAuditEvents(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String action) {

        UUID tenantId = TenantContext.requireTenantId();
        List<AuditEvent> events = auditService.getEvents(tenantId);

        // Optional action filter
        if (action != null && !action.isBlank()) {
            events = events.stream()
                    .filter(e -> action.equalsIgnoreCase(e.getAction()))
                    .collect(Collectors.toList());
        }

        // Optional pagination (non-destructive: omitting page/size returns all)
        if (page != null && size != null && size > 0) {
            int fromIndex = page * size;
            if (fromIndex >= events.size()) {
                events = List.of();
            } else {
                int toIndex = Math.min(fromIndex + size, events.size());
                events = events.subList(fromIndex, toIndex);
            }
        }

        return ResponseEntity.ok(events);
    }
}
