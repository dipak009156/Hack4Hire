package com.quantumlock.sharing;

import com.quantumlock.auth.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/shares")
@RequiredArgsConstructor
@Tag(name = "Sharing Overview", description = "Tenant and user-level access grant overviews")
public class SharesController {

    private final SharingService sharingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    @Operation(summary = "Get all shares for the current tenant")
    public ResponseEntity<List<AccessGrantResponse>> getAllShares() {
        return ResponseEntity.ok(sharingService.listAllGrants());
    }

    @GetMapping("/my-grants")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Get grants created by the authenticated user and grants shared with them")
    public ResponseEntity<Map<String, List<AccessGrantResponse>>> getMyGrants(
            @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(Map.of(
                "created", sharingService.listGrantsCreatedBy(principal.getUserId()),
                "received", sharingService.listGrantsSharedWith(principal.getUserId())
        ));
    }
}

