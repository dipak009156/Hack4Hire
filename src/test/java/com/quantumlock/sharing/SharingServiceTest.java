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

    private final UUID tenantId = UUID.randomUUID();
    private final UUID grantorId = UUID.randomUUID();
    private final UUID granteeId = UUID.randomUUID();
    private final UUID docId = UUID.randomUUID();
    private final AppUserDetails principal = new AppUserDetails(grantorId, tenantId, "alice", List.of("USER"), "eng", "HIGH");

    @BeforeEach
    void setUp() {
        sharingService = new SharingService(accessGrantRepository, documentRepository, auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("grantAccess creates grant, sets download quota and audit trail")
    void grantAccess_success() {
        Document doc = Document.builder().id(docId).tenantId(tenantId).ownerId(grantorId).name("doc.pdf").build();
        when(documentRepository.findByIdAndTenantId(docId, tenantId)).thenReturn(Optional.of(doc));

        AccessGrant saved = AccessGrant.builder()
                .id(UUID.randomUUID())
                .documentId(docId)
                .tenantId(tenantId)
                .grantorId(grantorId)
                .granteeId(granteeId)
                .permissions(List.of("READ"))
                .purpose("Audit review")
                .maxDownloads(3)
                .remainingDownloads(3)
                .build();

        when(accessGrantRepository.save(any(AccessGrant.class))).thenReturn(saved);

        ShareDocumentRequest req = new ShareDocumentRequest(granteeId, List.of("READ"), "Audit review", null, 3);
        AccessGrantResponse response = sharingService.grantAccess(docId, req, principal);

        assertThat(response.granteeId()).isEqualTo(granteeId);
        assertThat(response.remainingDownloads()).isEqualTo(3);
        assertThat(response.active()).isTrue();
        verify(auditService).record(eq(tenantId), eq(grantorId), eq("alice"), eq("ACCESS_GRANT"), eq(docId), eq("GRANT_ACCESS"), eq("SUCCESS"), any());
    }

    @Test
    @DisplayName("revokeAccess marks grant as revoked and audits")
    void revokeAccess_success() {
        UUID grantId = UUID.randomUUID();
        AccessGrant grant = AccessGrant.builder()
                .id(grantId)
                .documentId(docId)
                .tenantId(tenantId)
                .grantorId(grantorId)
                .granteeId(granteeId)
                .build();

        when(accessGrantRepository.findByIdAndTenantId(grantId, tenantId)).thenReturn(Optional.of(grant));

        sharingService.revokeAccess(docId, grantId, principal);

        assertThat(grant.getRevokedAt()).isNotNull();
        verify(accessGrantRepository).save(grant);
        verify(auditService).record(eq(tenantId), eq(grantorId), eq("alice"), eq("ACCESS_GRANT"), eq(docId), eq("REVOKE_ACCESS"), eq("SUCCESS"), any());
    }
}

