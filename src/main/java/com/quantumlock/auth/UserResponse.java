package com.quantumlock.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a user record — password hash is never included.
 */
public record UserResponse(
        UUID id,
        UUID tenantId,
        String username,
        List<String> roles,
        String department,
        String deviceTrustLevel,
        boolean active,
        Instant createdAt
) {}

