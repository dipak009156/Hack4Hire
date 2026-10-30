package com.quantumlock.auth;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String tenant,
        List<String> roles
) {
}
