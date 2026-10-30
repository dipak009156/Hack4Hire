package com.quantumlock.policy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PolicyRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Expression is required")
        String expression,

        Long version
) {}

