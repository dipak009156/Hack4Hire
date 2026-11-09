package com.quantumlock.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCredentialRequestDto(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Username is required")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$", message = "Username must be 3-32 alphanumeric characters, dashes, or underscores")
        String username,

        @NotBlank(message = "Tenant name is required")
        @Pattern(regexp = "^[a-z0-9-]{3,64}$", message = "Tenant name must be 3-64 lowercase alphanumeric characters or hyphens")
        String tenantName,

        String department,

        @NotBlank(message = "Business justification is required")
        String reason
) {}

