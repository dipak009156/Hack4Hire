package com.quantumlock.rotation;

import com.quantumlock.audit.AuditService;
import com.quantumlock.common.TenantContext;
import com.quantumlock.document.DocumentVersionRepository;
import com.quantumlock.kms.KmsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeyRotationServiceTest {

    @Mock
    private KeyRotationRepository keyRotationRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private KmsService kmsService;

    @Mock
    private AuditService auditService;

    private KeyRotationService keyRotationService;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
        keyRotationService = new KeyRotationService(
                keyRotationRepository, documentVersionRepository, kmsService, auditService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("startRotation creates a PENDING job and records an audit event")
    void startRotation_createsPendingJob() {
        when(keyRotationRepository.findActiveByTenantId(tenantId)).thenReturn(List.of());
        when(documentVersionRepository.countCommittedVersionsForRotation(tenantId, 1)).thenReturn(5L);

        KeyRotation savedJob = KeyRotation.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .sourceKekVersion(1)
                .targetKekVersion(2)
                .status("PENDING")
                .totalDocuments(5L)
                .build();

        when(keyRotationRepository.save(any(KeyRotation.class))).thenReturn(savedJob);

        KeyRotation result = keyRotationService.startRotation(1, 2, actorId, "admin");

        assertThat(result).isNotNull();
        assertThat(result.getSourceKekVersion()).isEqualTo(1);
        assertThat(result.getTargetKekVersion()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        verify(auditService).record(eq(tenantId), eq(actorId), eq("admin"),
                eq("KEY_ROTATION"), eq(result.getId()), eq("ROTATION_STARTED"), eq("SUCCESS"), any());
    }

    @Test
    @DisplayName("startRotation rejects when an active job already exists for the tenant")
    void startRotation_rejectsWhenActiveJobExists() {
        KeyRotation activeJob = KeyRotation.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status("IN_PROGRESS")
                .build();

        when(keyRotationRepository.findActiveByTenantId(tenantId)).thenReturn(List.of(activeJob));

        assertThatThrownBy(() -> keyRotationService.startRotation(1, 2, actorId, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A rotation job is already active");
    }

    @Test
    @DisplayName("getRotationStatus returns the status of an existing job")
    void getRotationStatus_returnsJob() {
        UUID jobId = UUID.randomUUID();
        KeyRotation job = KeyRotation.builder()
                .id(jobId)
                .tenantId(tenantId)
                .sourceKekVersion(1)
                .targetKekVersion(2)
                .status("COMPLETED")
                .totalDocuments(10L)
                .processedDocuments(10L)
                .build();

        when(keyRotationRepository.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.of(job));

        KeyRotation result = keyRotationService.getStatus(jobId);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getProcessedDocuments()).isEqualTo(10L);
    }
}
