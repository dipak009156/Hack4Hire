package com.quantumlock.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantumlock.audit.AuditService;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RecoveryService} (ADR-004, Invariant I1, Q09).
 */
@ExtendWith(MockitoExtension.class)
class RecoveryServiceTest {

    private static final String KEK_V1 = "u7x8Q4z+1vP3mN9kL2jH5gF8dE1cA4bZ7yX0wV3uT6s=";

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentVersionRepository documentVersionRepository;
    @Mock private ObjectStoreService objectStoreService;
    @Mock private OutboxRepository outboxRepository;
    @Mock private AuditService auditService;

    private RecoveryService recoveryService;
    private EnvelopeEncryptionService encryptionService;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID actorId  = UUID.fromString("11111111-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private final UUID docId    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        KmsProperties props = new KmsProperties(1, false, Map.of(1, KEK_V1));
        SimulatedKmsService kms = new SimulatedKmsService(props);
        encryptionService = new EnvelopeEncryptionService(kms);

        recoveryService = new RecoveryService(
                documentRepository,
                documentVersionRepository,
                objectStoreService,
                encryptionService,
                outboxRepository,
                auditService,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("reconcile completes commit for verified OBJECT_STORED version whose ciphertext matches digest")
    void reconcile_verifiedObjectStored_completesCommit() {
        byte[] ciphertext = "encrypted-content-sample".getBytes(StandardCharsets.UTF_8);
        String expectedDigest = encryptionService.computeDigest(ciphertext);

        Document doc = Document.builder()
                .id(docId).tenantId(tenantId).name("doc.txt").currentVersion(0)
                .build();

        DocumentVersion dv = DocumentVersion.builder()
                .id(UUID.randomUUID())
                .document(doc)
                .tenantId(tenantId)
                .version(1)
                .objectKey("tenants/" + tenantId + "/documents/" + docId + "/v1/obj")
                .ciphertextDigest(expectedDigest)
                .state(DocumentState.OBJECT_STORED)
                .createdAt(Instant.now().minusSeconds(600))
                .build();

        when(documentVersionRepository.findStaleUploads(any())).thenReturn(List.of(dv));
        when(documentVersionRepository.compareAndSetState(dv.getId(), tenantId, DocumentState.OBJECT_STORED, DocumentState.RECOVERING))
                .thenReturn(1); // CAS succeeds
        when(objectStoreService.objectExists(dv.getObjectKey())).thenReturn(true);
        when(objectStoreService.getObject(dv.getObjectKey())).thenReturn(ciphertext);

        com.quantumlock.common.TenantContext.setTenantId(tenantId);
        try {
            RecoveryService.ReconcileResult result = recoveryService.reconcile(actorId, "admin");

            assertThat(result.staleRecordsFound()).isEqualTo(1);
            assertThat(result.recovered()).isEqualTo(1);
            assertThat(result.failed()).isEqualTo(0);
            assertThat(result.objectsDeleted()).isEqualTo(0);

            // Document updated to version 1
            assertThat(doc.getCurrentVersion()).isEqualTo(1);
            verify(documentRepository).save(doc);

            // Version transitioned to COMMITTED
            assertThat(dv.getState()).isEqualTo(DocumentState.COMMITTED);
            verify(documentVersionRepository).save(dv);

            // Outbox event enqueued
            verify(outboxRepository).save(any(OutboxEvent.class));

        } finally {
            com.quantumlock.common.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("reconcile cleans up orphaned object and marks FAILED for corrupted ciphertext")
    void reconcile_corruptedCiphertext_deletesObjectAndMarksFailed() {
        byte[] validCiphertext = "valid-ciphertext".getBytes(StandardCharsets.UTF_8);
        String expectedDigest = encryptionService.computeDigest(validCiphertext);

        byte[] corruptedCiphertext = "tampered-ciphertext".getBytes(StandardCharsets.UTF_8);

        Document doc = Document.builder()
                .id(docId).tenantId(tenantId).name("doc.txt").currentVersion(0)
                .build();

        DocumentVersion dv = DocumentVersion.builder()
                .id(UUID.randomUUID())
                .document(doc)
                .tenantId(tenantId)
                .version(1)
                .objectKey("tenants/" + tenantId + "/documents/" + docId + "/v1/obj")
                .ciphertextDigest(expectedDigest)
                .state(DocumentState.OBJECT_STORED)
                .createdAt(Instant.now().minusSeconds(600))
                .build();

        when(documentVersionRepository.findStaleUploads(any())).thenReturn(List.of(dv));
        when(documentVersionRepository.compareAndSetState(dv.getId(), tenantId, DocumentState.OBJECT_STORED, DocumentState.RECOVERING))
                .thenReturn(1);
        when(objectStoreService.objectExists(dv.getObjectKey())).thenReturn(true);
        when(objectStoreService.getObject(dv.getObjectKey())).thenReturn(corruptedCiphertext);

        com.quantumlock.common.TenantContext.setTenantId(tenantId);
        try {
            RecoveryService.ReconcileResult result = recoveryService.reconcile(actorId, "admin");

            assertThat(result.recovered()).isEqualTo(0);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.objectsDeleted()).isEqualTo(1);

            // Orphaned corrupted object deleted
            verify(objectStoreService).deleteObject(dv.getObjectKey());

            // State transitioned to FAILED
            assertThat(dv.getState()).isEqualTo(DocumentState.FAILED);
            verify(documentVersionRepository).save(dv);

            // Outbox NOT called
            verify(outboxRepository, never()).save(any());

        } finally {
            com.quantumlock.common.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("reconcile deletes object and marks FAILED for stale PENDING version without object")
    void reconcile_stalePending_marksFailedAndDeletesObject() {
        Document doc = Document.builder()
                .id(docId).tenantId(tenantId).name("doc.txt").currentVersion(0)
                .build();

        DocumentVersion dv = DocumentVersion.builder()
                .id(UUID.randomUUID())
                .document(doc)
                .tenantId(tenantId)
                .version(1)
                .objectKey("tenants/" + tenantId + "/documents/" + docId + "/v1/pending-key")
                .ciphertextDigest("some-digest")
                .state(DocumentState.PENDING)
                .createdAt(Instant.now().minusSeconds(600))
                .build();

        when(documentVersionRepository.findStaleUploads(any())).thenReturn(List.of(dv));
        when(documentVersionRepository.compareAndSetState(dv.getId(), tenantId, DocumentState.PENDING, DocumentState.RECOVERING))
                .thenReturn(1);
        when(objectStoreService.objectExists(dv.getObjectKey())).thenReturn(false);

        com.quantumlock.common.TenantContext.setTenantId(tenantId);
        try {
            RecoveryService.ReconcileResult result = recoveryService.reconcile(actorId, "admin");

            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.objectsDeleted()).isEqualTo(1);

            verify(objectStoreService).deleteObject(dv.getObjectKey());
            assertThat(dv.getState()).isEqualTo(DocumentState.FAILED);

        } finally {
            com.quantumlock.common.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("reconcile skips in-flight upload when compare-and-set claim fails")
    void reconcile_inFlightUpload_skippedSafely() {
        DocumentVersion dv = DocumentVersion.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .version(1)
                .state(DocumentState.PENDING)
                .createdAt(Instant.now().minusSeconds(600))
                .build();

        when(documentVersionRepository.findStaleUploads(any())).thenReturn(List.of(dv));
        // CAS returns 0 because concurrent thread changed state to OBJECT_STORED or COMMITTED
        when(documentVersionRepository.compareAndSetState(dv.getId(), tenantId, DocumentState.PENDING, DocumentState.RECOVERING))
                .thenReturn(0);

        com.quantumlock.common.TenantContext.setTenantId(tenantId);
        try {
            RecoveryService.ReconcileResult result = recoveryService.reconcile(actorId, "admin");

            assertThat(result.recovered()).isEqualTo(0);
            assertThat(result.failed()).isEqualTo(0);
            // MinIO object is NOT touched
            verify(objectStoreService, never()).deleteObject(any());

        } finally {
            com.quantumlock.common.TenantContext.clear();
        }
    }
}
