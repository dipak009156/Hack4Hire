package com.quantumlock.document;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for a single document resource. */
@Builder
public record DocumentResponse(
        UUID id,
        UUID tenantId,
        UUID ownerId,
        String name,
        String classification,
        int currentVersion,
        Instant createdAt,
        Instant updatedAt
) {}

