package com.quantumlock.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for creating a new user within the caller's tenant.
 * Role must be one of: ADMIN, USER, AUDITOR.
 * Tenant is derived exclusively from the caller's JWT — never from this payload.
 */
public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        List<String> roles,

        String department,

        String deviceTrustLevel
) {}

