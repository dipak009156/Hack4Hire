package com.quantumlock.policy;

import com.quantumlock.audit.AuditService;
import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private AuditService auditService;

    private PolicyService policyService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final AppUserDetails principal = new AppUserDetails(userId, tenantId, "admin", List.of("ADMIN"), "security", "HIGH");

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(policyRepository, auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("createPolicy creates policy and records audit event")
    void createPolicy_success() {
        PolicyRequest req = new PolicyRequest("confidential-access", "classification == 'CONFIDENTIAL'", 1L);
        Policy saved = Policy.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name(req.name())
                .expression(req.expression())
                .version(1L)
                .updatedBy("admin")
                .build();

        when(policyRepository.save(any(Policy.class))).thenReturn(saved);

        PolicyDto result = policyService.createPolicy(req, principal);

        assertThat(result.name()).isEqualTo("confidential-access");
        assertThat(result.version()).isEqualTo(1L);
        verify(auditService, times(1)).record(eq(tenantId), eq(userId), eq("admin"), eq("POLICY"), any(), eq("POLICY_CREATED"), eq("SUCCESS"), any());
    }

    @Test
    @DisplayName("updatePolicy throws 409 conflict when version does not match")
    void updatePolicy_conflict_throwsOptimisticLockingFailureException() {
        UUID policyId = UUID.randomUUID();
        Policy current = Policy.builder()
                .id(policyId)
                .tenantId(tenantId)
                .name("test-policy")
                .expression("true")
                .version(2L)
                .build();

        when(policyRepository.findByIdAndTenantId(policyId, tenantId)).thenReturn(Optional.of(current));

        // Caller submits expected version 1L but current is 2L
        PolicyRequest req = new PolicyRequest("test-policy", "false", 1L);

        assertThatThrownBy(() -> policyService.updatePolicy(policyId, req, principal))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("modified by another transaction");
    }
}

