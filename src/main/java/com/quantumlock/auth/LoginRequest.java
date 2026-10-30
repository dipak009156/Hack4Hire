package com.quantumlock.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password,

        @JsonAlias({"tenantName", "tenant_name", "tenantId"})
        String tenant
) {
    public String tenant() {
        return tenant != null ? tenant : "tenant-alpha";
    }
}
