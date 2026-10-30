package com.quantumlock.policy;

import com.quantumlock.auth.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
@Tag(name = "Policies", description = "Tenant policy management (Admin only)")
public class PolicyController {

    private final PolicyService policyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "List all policies for tenant")
    public ResponseEntity<List<PolicyDto>> listPolicies() {
        return ResponseEntity.ok(policyService.listPolicies());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get policy by ID")
    public ResponseEntity<PolicyDto> getPolicy(@PathVariable UUID id) {
        return ResponseEntity.ok(policyService.getPolicy(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create new policy")
    public ResponseEntity<PolicyDto> createPolicy(
            @Valid @RequestBody PolicyRequest request,
            @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(policyService.createPolicy(request, principal));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update policy (with optimistic concurrency check)")
    public ResponseEntity<PolicyDto> updatePolicy(
            @PathVariable UUID id,
            @Valid @RequestBody PolicyRequest request,
            @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(policyService.updatePolicy(id, request, principal));
    }
}

