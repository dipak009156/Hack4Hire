package com.quantumlock.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantumlock.audit.AuditEventRepository;
import com.quantumlock.audit.AuditService;
import com.quantumlock.auth.Tenant;
import com.quantumlock.auth.TenantRepository;
import com.quantumlock.crypto.EnvelopeEncryptionService;
import com.quantumlock.kms.KmsProperties;
import com.quantumlock.kms.SimulatedKmsService;
import com.quantumlock.outbox.OutboxEvent;
import com.quantumlock.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DocumentService} verifying upload state machine and audit integration.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final String KEK_V1 = "u7x8Q4z+1vP3mN9kL2jH5gF8dE1cA4bZ7yX0wV3uT6s=";

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentVersionRepository documentVersionRepository;
    @Mock private ObjectStoreService objectStoreService;
    @Mock private OutboxRepository outboxRepository;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private TenantRepository tenantRepository;

    private DocumentService documentService;
    private AuditService auditService;
    private EnvelopeEncryptionService encryptionService;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID ownerId  = UUID.fromString("11111111-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private final UUID docId    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        KmsProperties props = new KmsProperties(1, false, Map.of(1, KEK_V1));
        SimulatedKmsService kms = new SimulatedKmsService(props);
        encryptionService = new EnvelopeEncryptionService(kms);
        auditService = new AuditService(auditEventRepository, tenantRepository);

        lenient().when(tenantRepository.findByIdForUpdate(tenantId))
                .thenReturn(Optional.of(Tenant.builder().id(tenantId).name("tenant-alpha").build()));

        documentService = new DocumentService(
                documentRepository,
                documentVersionRepository,
                encryptionService,
                objectStoreService,
                outboxRepository,
                auditService,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("uploadNewDocument creates document, encrypts content, sets OBJECT_STORED, commits")
    void uploadNewDocument_happyPath() {
        String plaintext = "Sensitive document content";
        String content = Base64.getEncoder().encodeToString(plaintext.getBytes());
        UploadDocumentRequest request = new UploadDocumentRequest(
                "test-doc.txt", "INTERNAL", "text/plain", content);

        Document savedDoc = Document.builder()
                .id(docId).tenantId(tenantId).ownerId(ownerId)
                .name("test-doc.txt").classification("INTERNAL").currentVersion(0)
                .build();

        DocumentVersion savedDv = DocumentVersion.builder()
                .id(UUID.randomUUID()).document(savedDoc).tenantId(tenantId)
                .version(1).state(DocumentState.PENDING)
                .objectKey("tenants/" + tenantId + "/documents/" + docId + "/v1/x")
                .ciphertextDigest("abc").nonce("nonce").wrappedDek("dek").kekVersion(1)
                .build();

        when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);
        when(documentVersionRepository.saveAndFlush(any(DocumentVersion.class))).thenReturn(savedDv);
        when(auditEventRepository.findTopByTenantIdOrderByChainPositionDesc(any())).thenReturn(Optional.empty());
        when(auditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        com.quantumlock.common.TenantContext.setTenantId(tenantId);
        try {
            com.quantumlock.auth.AppUserDetails principal =
                    new com.quantumlock.auth.AppUserDetails(ownerId, tenantId, "user1",
                            List.of("USER"), "eng", "HIGH");

            DocumentResponse response = documentService.uploadNewDocument(request, principal);

            assertThat(response).isNotNull();
            assertThat(response.tenantId()).isEqualTo(tenantId);
            assertThat(response.name()).isEqualTo("test-doc.txt");

            // MinIO put was called
            verify(objectStoreService, times(1)).putObject(anyString(), any(byte[].class), anyString());

            // Outbox event was saved
            verify(outboxRepository, times(1)).save(any(OutboxEvent.class));

            // Audit was recorded
            verify(auditEventRepository, times(1)).save(any());

        } finally {
            com.quantumlock.common.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("uploadNewDocument: MinIO failure transitions version to FAILED and rethrows")
    void uploadNewDocument_minioFailure_leavesVersionFailed() {
        String content = Base64.getEncoder().encodeToString("data".getBytes());
        UploadDocumentRequest request = new UploadDocumentRequest(
                "fail-doc.txt", "INTERNAL", "text/plain", content);

        Document savedDoc = Document.builder()
                .id(docId).tenantId(tenantId).ownerId(ownerId)
                .name("fail-doc.txt").classification("INTERNAL").currentVersion(0)
                .build();

        DocumentVersion savedDv = DocumentVersion.builder()
                .id(UUID.randomUUID()).document(savedDoc).tenantId(tenantId)
                .version(1).state(DocumentState.PENDING)
                .objectKey("key").ciphertextDigest("d").nonce("n").wrappedDek("w").kekVersion(1)
                .build();

        when(documentRepository.save(any())).thenReturn(savedDoc);
        when(documentVersionRepository.saveAndFlush(any())).thenReturn(savedDv);
        doThrow(new ObjectStoreException("MinIO down", new RuntimeException()))
                .when(objectStoreService).putObject(anyString(), any(), anyString());

        com.quantumlock.common.TenantContext.setTenantId(tenantId);
        try {
            com.quantumlock.auth.AppUserDetails principal =
                    new com.quantumlock.auth.AppUserDetails(ownerId, tenantId, "user1",
                            List.of("USER"), "eng", "HIGH");

            assertThatThrownBy(() -> documentService.uploadNewDocument(request, principal))
                    .isInstanceOf(ObjectStoreException.class);

            // Version state was saved as FAILED
            verify(documentVersionRepository, atLeastOnce()).saveAndFlush(argThat(
                    dv -> dv.getState() == DocumentState.FAILED));

            // Outbox NEVER called on failure
            verify(outboxRepository, never()).save(any());

        } finally {
            com.quantumlock.common.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("digestOf produces stable SHA-256 hex")
    void digestOf_stable() {
        String d1 = IdempotencyService.digestOf("payload-1");
        String d2 = IdempotencyService.digestOf("payload-1");
        assertThat(d1).isEqualTo(d2).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("buildObjectKey produces expected tenant-partitioned path")
    void buildObjectKey_format() {
        UUID tenant = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID doc    = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID upload = UUID.fromString("33333333-3333-3333-3333-333333333333");

        String key = ObjectStoreService.buildObjectKey(tenant, doc, 3, upload);

        assertThat(key).startsWith("tenants/11111111")
                       .contains("documents/22222222")
                       .contains("v3/33333333");
    }
}
