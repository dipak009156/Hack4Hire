package com.quantumlock.auth;

import jakarta.validation.constraints.NotBlank;

public record RegisterTenantRequest(
        @NotBlank(message = "tenantName is required")
        String tenantName,

        @NotBlank(message = "adminUsername is required")
        String adminUsername,

        @NotBlank(message = "adminPassword is required")
        String adminPassword
) {}
