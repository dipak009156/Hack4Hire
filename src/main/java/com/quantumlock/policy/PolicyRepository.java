package com.quantumlock.policy;

import com.quantumlock.common.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PolicyRepository extends TenantAwareRepository<Policy, UUID> {
    Optional<Policy> findByNameAndTenantId(String name, UUID tenantId);
    Optional<Policy> findByPolicyIdAndTenantId(UUID policyId, UUID tenantId);
}
