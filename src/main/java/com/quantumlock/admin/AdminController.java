package com.quantumlock.admin;

import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.document.RecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin-only operations.
 *
 * <ul>
 *   <li>{@code POST /api/v1/admin/reconcile} — recover stale uploads (ADR-004)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative operations — ADMIN role required")
public class AdminController {

    private final RecoveryService recoveryService;

    @PostMapping("/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reconcile stale document uploads",
               description = "Marks PENDING/OBJECT_STORED uploads older than 5 min as FAILED or COMMITTED, and cleans up orphaned MinIO objects.")
    public ResponseEntity<Map<String, Object>> reconcile(
            @AuthenticationPrincipal AppUserDetails principal) {

        RecoveryService.ReconcileResult result = recoveryService.reconcile(
                principal.getUserId(), principal.getUsername());

        return ResponseEntity.ok(Map.of(
                "staleRecordsFound", result.staleRecordsFound(),
                "recovered", result.recovered(),
                "markedFailed", result.failed(),
                "objectsDeleted", result.objectsDeleted()
        ));
    }
}
