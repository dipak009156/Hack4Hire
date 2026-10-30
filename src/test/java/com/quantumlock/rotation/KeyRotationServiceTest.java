package com.quantumlock.rotation;

import com.quantumlock.audit.AuditService;
import com.quantumlock.common.TenantContext;
import com.quantumlock.document.DocumentVersion;
import com.quantumlock.document.DocumentVersionRepository;
import com.quantumlock.kms.KmsService;
import com.quantumlock.kms.WrappedDek;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        keyRotationService = new KeyRotationService(
                keyRotationRepository,
                documentVersionRepository,
                kmsService,
                auditService
        );
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("startRotation rewraps versions, updates kekVersion to target, marks job COMPLETED")
    void startRotation_rewrapsVersionsAndCompletes() {
        when(keyRotationRepository.findActiveByTenantId(tenantId)).thenReturn(List.of());
        when(documentVersionRepository.countCommittedVersionsForRotation(tenantId, 1)).thenReturn(1L);

        UUID jobId = UUID.randomUUID();
        KeyRotation job = KeyRotation.builder()
                .id(jobId)
                .tenantId(tenantId)
                .sourceKekVersion(1)
                .targetKekVersion(2)
                .status("PENDING")
                .totalDocuments(1L)
                .build();

        when(keyRotationRepository.save(any(KeyRotation.class))).thenAnswer(i -> i.getArgument(0));

        UUID verId = UUID.randomUUID();
        byte[] oldDekPlaintext = "thirty-two-bytes-plain-dek-here!".getBytes();
        byte[] oldWrapped = "old-wrapped-bytes".getBytes();
        byte[] newWrapped = "new-wrapped-bytes".getBytes();

        DocumentVersion dv = DocumentVersion.builder()
                .id(verId)
                .tenantId(tenantId)
                .kekVersion(1)
                .wrappedDek(Base64.getEncoder().encodeToString(oldWrapped))
                .build();

        // First batch returns 1 version, second batch empty to exit loop
        when(documentVersionRepository.findCommittedVersionsForRotation(eq(tenantId), eq(1), any(), any()))
                .thenReturn(List.of(dv))
                .thenReturn(List.of());

        when(kmsService.unwrapDek(any())).thenReturn(oldDekPlaintext);
        when(kmsService.wrapDekWithVersion(any(), eq(2))).thenReturn(new WrappedDek(newWrapped, 2));

        KeyRotation result = keyRotationService.startRotation(1, 2, actorId, "admin");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(dv.getKekVersion()).isEqualTo(2);
        assertThat(dv.getWrappedDek()).isEqualTo(Base64.getEncoder().encodeToString(newWrapped));
        verify(documentVersionRepository).save(dv);
        verify(auditService).record(eq(tenantId), eq(actorId), eq("admin"), eq("KEY_ROTATION"), any(), eq("ROTATION_COMPLETED"), eq("SUCCESS"), any());
    }
}
