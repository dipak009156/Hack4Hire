package com.quantumlock.auth;

/**
 * Request DTO for updating a user's role or active status.
 * All fields are optional — only non-null fields are applied.
 * newPassword is optional: if null or blank, the password is not changed.
 */
public record UpdateUserRequest(
        java.util.List<String> roles,
        String department,
        String deviceTrustLevel,
        Boolean active,
        String newPassword      // optional — blank/null means "no change"
) {}

