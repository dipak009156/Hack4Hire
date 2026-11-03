package com.quantumlock.sharing;

import com.quantumlock.audit.AuditService;
import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.common.TenantContext;
import com.quantumlock.document.Document;
import com.quantumlock.document.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SharingServiceTest {

    @Mock
    private AccessGrantRepository accessGrantRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private AuditService auditService;

    private SharingService sharingService;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID ownerId = UUID.randomUUID();
    private final UUID granteeId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private AppUserDetails principal;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
        principal = new AppUserDetails(ownerId, tenantId, "dr_alice", List.of("ADMIN"), "security", "HIGH");
        sharingService = new SharingService(accessGrantRepository, documentRepository, auditService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("grantAccess creates active grant with quota and records audit event")
    void grantAccess_success() {
        Document doc = Document.builder()
                .id(documentId)
                .tenantId(tenantId)
                .ownerId(ownerId)
                .name("Health Record")
                .classification("CONFIDENTIAL")
                .build();

        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(doc));

        AccessGrant savedGrant = AccessGrant.builder()
                .id(UUID.randomUUID())
                .documentId(documentId)
                .tenantId(tenantId)
                .grantorId(ownerId)
                .granteeId(granteeId)
                .permissions(List.of("DOWNLOAD"))
                .maxDownloads(2)
                .remainingDownloads(2)
                .build();

        when(accessGrantRepository.save(any(AccessGrant.class))).thenReturn(savedGrant);

        ShareDocumentRequest request = new ShareDocumentRequest(
                granteeId, documentId, List.of("DOWNLOAD"), "Medical Consult", null, 2);

        AccessGrantResponse response = sharingService.grantAccess(documentId, request, principal);

        assertThat(response).isNotNull();
        assertThat(response.granteeId()).isEqualTo(granteeId);
        assertThat(response.maxDownloads()).isEqualTo(2);

        verify(auditService).record(eq(tenantId), eq(ownerId), eq("dr_alice"),
                eq("ACCESS_GRANT"), eq(documentId), eq("GRANT_ACCESS"), eq("SUCCESS"), any());
    }

    @Test
    @DisplayName("revokeAccess linearizes grant by setting revokedAt and records audit event")
    void revokeAccess_success() {
        UUID grantId = UUID.randomUUID();
        AccessGrant grant = AccessGrant.builder()
                .id(grantId)
                .documentId(documentId)
                .tenantId(tenantId)
                .grantorId(ownerId)
                .granteeId(granteeId)
                .build();

        when(accessGrantRepository.findByIdAndTenantId(grantId, tenantId)).thenReturn(Optional.of(grant));

        sharingService.revokeAccess(documentId, grantId, principal);

        ArgumentCaptor<AccessGrant> captor = ArgumentCaptor.forClass(AccessGrant.class);
        verify(accessGrantRepository).save(captor.capture());

        AccessGrant updated = captor.getValue();
        assertThat(updated.getRevokedAt()).isNotNull();

        verify(auditService).record(eq(tenantId), eq(ownerId), eq("dr_alice"),
                eq("ACCESS_GRANT"), eq(documentId), eq("REVOKE_ACCESS"), eq("SUCCESS"), any());
    }

    @Test
    @DisplayName("revokeAccess is idempotent when grant is already revoked")
    void revokeAccess_alreadyRevoked_isIdempotent() {
        UUID grantId = UUID.randomUUID();
        AccessGrant grant = AccessGrant.builder()
                .id(grantId)
                .documentId(documentId)
                .tenantId(tenantId)
                .grantorId(ownerId)
                .granteeId(granteeId)
                .revokedAt(Instant.now().minusSeconds(60))
                .build();

        when(accessGrantRepository.findByIdAndTenantId(grantId, tenantId)).thenReturn(Optional.of(grant));

        sharingService.revokeAccess(documentId, grantId, principal);

        verify(accessGrantRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
