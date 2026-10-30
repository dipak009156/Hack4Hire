package com.quantumlock.auth;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateUserRequest(
        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "password is required")
        String password,

        List<String> roles,

        Integer clearanceLevel
) {
    public List<String> roles() {
        return roles == null || roles.isEmpty() ? List.of("MEMBER") : roles;
    }
}
