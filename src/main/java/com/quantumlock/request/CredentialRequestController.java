package com.quantumlock.request;

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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/credential-requests")
@RequiredArgsConstructor
@Tag(name = "Credential Requests", description = "Tenant credential onboarding and administrative review workflow")
public class CredentialRequestController {

    private final CredentialRequestService service;

    @PostMapping
    @Operation(summary = "Submit a credential request for a new isolated tenant",
               description = "Public endpoint. Validates tenant redundancy, persists request, and triggers review email alerts.")
    public ResponseEntity<CredentialRequestResponse> submitRequest(
            @Valid @RequestBody CreateCredentialRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRequest(dto));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List all credential requests", description = "Platform SUPER_ADMIN only. Returns chronological requests.")
    public ResponseEntity<List<CredentialRequestResponse>> listRequests() {
        return ResponseEntity.ok(service.listAllRequests());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get credential request details by ID")
    public ResponseEntity<CredentialRequestResponse> getRequest(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getRequest(id));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Approve or reject a credential request",
               description = "Platform SUPER_ADMIN only. On approval, provisions new isolated tenant and dispatches credentials via email.")
    public ResponseEntity<CredentialRequestResponse> reviewRequest(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewCredentialRequestDto dto,
            @AuthenticationPrincipal AppUserDetails reviewer) {
        return ResponseEntity.ok(service.reviewRequest(id, dto, reviewer));
    }

    @GetMapping("/emails")
    @Operation(summary = "List sent email notifications", description = "Simulated email inbox for interactive review.")
    public ResponseEntity<List<EmailNotificationResponse>> listEmails() {
        return ResponseEntity.ok(service.listEmails());
    }

    @PostMapping("/emails/{id}/read")
    @Operation(summary = "Mark email notification as read")
    public ResponseEntity<Void> markEmailRead(@PathVariable UUID id) {
        service.markEmailRead(id);
        return ResponseEntity.ok().build();
    }
}

