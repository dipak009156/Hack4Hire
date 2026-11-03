package com.quantumlock.sharing;

import com.quantumlock.auth.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/grants")
@RequiredArgsConstructor
@Tag(name = "Access Grants", description = "Document access grant and revocation APIs")
public class GrantsController {

    private final SharingService sharingService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Issue a document access grant", description = "Grants access with optional expiry and download quota.")
    public ResponseEntity<AccessGrantResponse> createGrant(
            @RequestBody ShareDocumentRequest request,
            @AuthenticationPrincipal AppUserDetails principal) {

        if (request.documentId() == null) {
            throw new IllegalArgumentException("documentId is required");
        }

        AccessGrantResponse response = sharingService.grantAccess(request.documentId(), request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{grantId}/revoke")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Revoke an access grant", description = "Linearly revokes the grant. Subsequent download attempts will receive HTTP 410 GONE.")
    public ResponseEntity<Void> revokeGrant(
            @PathVariable UUID grantId,
            @AuthenticationPrincipal AppUserDetails principal) {

        sharingService.revokeAccess(grantId, principal);
        return ResponseEntity.ok().build();
    }
}
