package com.quantumlock.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record LoginResponse(
        @JsonProperty("accessToken")
        String accessToken,

        @JsonProperty("tokenType")
        String tokenType,

        @JsonProperty("expiresIn")
        long expiresIn,

        @JsonProperty("tenant")
        String tenant,

        @JsonProperty("roles")
        List<String> roles,

        @JsonProperty("userId")
        UUID userId,

        @JsonProperty("tenantId")
        UUID tenantId
) {
    @JsonProperty("token")
    public String token() {
        return accessToken;
    }
}
