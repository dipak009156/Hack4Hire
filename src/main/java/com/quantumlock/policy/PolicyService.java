package com.quantumlock.policy;

import com.quantumlock.audit.AuditService;
import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.common.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<PolicyDto> listPolicies() {
        UUID tenantId = TenantContext.requireTenantId();
        return policyRepository.findAllByTenantId(tenantId)
                .stream()
                .map(PolicyDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PolicyDto getPolicy(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        return policyRepository.findByIdAndTenantId(id, tenantId)
                .map(PolicyDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException("Policy not found"));
    }

    @Transactional
    public PolicyDto createPolicy(PolicyRequest request, AppUserDetails principal) {
        UUID tenantId = TenantContext.requireTenantId();

        Policy policy = Policy.builder()
                .tenantId(tenantId)
                .name(request.name())
                .expression(request.expression())
                .version(1L)
                .updatedBy(principal.getUsername())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Policy saved = policyRepository.saveAndFlush(policy);

        auditService.record(tenantId, principal.getUserId(), principal.getUsername(),
                "POLICY", saved.getId(), "POLICY_CREATED", "SUCCESS",
                "name=" + saved.getName());

        return PolicyDto.fromEntity(saved);
    }

    @Transactional
    public PolicyDto updatePolicy(UUID id, PolicyRequest request, AppUserDetails principal) {
        UUID tenantId = TenantContext.requireTenantId();
        Policy policy = policyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Policy not found"));

        if (request.version() != null && !request.version().equals(policy.getVersion())) {
            throw new OptimisticLockingFailureException(
                    "Policy has been modified by another transaction. Expected version=" +
                    request.version() + " but current version=" + policy.getVersion());
        }

        policy.setName(request.name());
        policy.setExpression(request.expression());
        policy.setUpdatedBy(principal.getUsername());
        policy.setUpdatedAt(Instant.now());

        Policy updated = policyRepository.saveAndFlush(policy);

        auditService.record(tenantId, principal.getUserId(), principal.getUsername(),
                "POLICY", updated.getId(), "POLICY_UPDATED", "SUCCESS",
                "name=" + updated.getName() + " newVersion=" + updated.getVersion());

        return PolicyDto.fromEntity(updated);
    }
}
