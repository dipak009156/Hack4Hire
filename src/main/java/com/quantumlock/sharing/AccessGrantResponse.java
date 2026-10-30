package com.quantumlock.sharing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for an access grant.
 */
public record AccessGrantResponse(
        UUID id,
        UUID documentId,
        UUID grantorId,
        UUID granteeId,
        List<String> permissions,
        String purpose,
        Instant expiresAt,
        Integer maxDownloads,
        Integer remainingDownloads,
        boolean active,
        Instant revokedAt,
        Instant createdAt
) {}

