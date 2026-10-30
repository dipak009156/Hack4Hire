package com.quantumlock.policy;

import java.time.Instant;
import java.util.UUID;

public record PolicyDto(
        UUID id,
        UUID policyId,
        UUID tenantId,
        String name,
        String expression,
        Long version,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static PolicyDto fromEntity(Policy p) {
        return new PolicyDto(
                p.getId(),
                p.getPolicyId(),
                p.getTenantId(),
                p.getName(),
                p.getExpression(),
                p.getVersion(),
                p.getUpdatedBy(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}

