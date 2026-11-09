package com.quantumlock.request;

import java.time.Instant;
import java.util.UUID;

public record CredentialRequestResponse(
        UUID id,
        String fullName,
        String email,
        String username,
        String tenantName,
        String department,
        String reason,
        String status,
        String rejectionReason,
        Instant createdAt,
        Instant reviewedAt,
        String reviewedBy
) {
    public static CredentialRequestResponse fromEntity(CredentialRequest r) {
        return new CredentialRequestResponse(
                r.getId(),
                r.getFullName(),
                r.getEmail(),
                r.getUsername(),
                r.getTenantName(),
                r.getDepartment(),
                r.getReason(),
                r.getStatus(),
                r.getRejectionReason(),
                r.getCreatedAt(),
                r.getReviewedAt(),
                r.getReviewedBy()
        );
    }
}

