package com.quantumlock.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewCredentialRequestDto(
        @NotBlank(message = "Action must be APPROVE or REJECT")
        @Pattern(regexp = "^(APPROVE|REJECT)$", message = "Action must be either APPROVE or REJECT")
        String action,

        String rejectionReason,

        String temporaryPassword
) {}

