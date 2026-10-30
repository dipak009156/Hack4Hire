package com.quantumlock.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantumlock.audit.AuditService;
import com.quantumlock.common.TenantContext;
import com.quantumlock.crypto.EnvelopeEncryptionService;
import com.quantumlock.outbox.OutboxEvent;
import com.quantumlock.outbox.OutboxRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Recovery reconciliation service for incomplete or orphaned uploads (ADR-004, Invariant I1).
 *
 * <h3>Reconciliation Flow</h3>
 * <ol>
 *   <li>Scan for uploads in {@code PENDING}, {@code OBJECT_STORED}, or {@code RECOVERING} state older than 5 min.</li>
 *   <li>Enforce tenant isolation: process strictly within the caller's tenant boundary.</li>
 *   <li>Atomically claim the upload using compare-and-set to {@code RECOVERING} so in-flight uploads are not disrupted.</li>
 *   <li>If ciphertext exists in MinIO and its SHA-256 digest verifies against recorded metadata:
 *       complete the upload ({@code COMMITTED}, parent version update, outbox, audit).</li>
 *   <li>Otherwise: mark {@code FAILED} and delete the orphaned MinIO object (for both {@code PENDING} and {@code OBJECT_STORED}).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);
    private static final long STALE_THRESHOLD_MINUTES = 5;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final ObjectStoreService objectStoreService;
    private final EnvelopeEncryptionService encryptionService;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Builder
    public record ReconcileResult(
            int staleRecordsFound,
            int recovered,
            int failed,
            int objectsDeleted
    ) {}

    @Transactional
    public ReconcileResult reconcile(UUID actorId, String actorName) {
        UUID tenantId = TenantContext.requireTenantId();
        Instant cutoff = Instant.now().minusSeconds(STALE_THRESHOLD_MINUTES * 60);

        List<DocumentVersion> stale = documentVersionRepository.findStaleUploads(cutoff);
        // Tenant boundary enforcement
        List<DocumentVersion> tenantStale = stale.stream()
                .filter(dv -> tenantId.equals(dv.getTenantId()))
                .toList();

        int recoveredCount = 0;
        int failedCount = 0;
        int objectsDeletedCount = 0;

        for (DocumentVersion dv : tenantStale) {
            DocumentState originalState = dv.getState();

            // Compare-and-set claim to RECOVERING prevents failing in-flight uploads
            int claimed = documentVersionRepository.compareAndSetState(
                    dv.getId(), tenantId, originalState, DocumentState.RECOVERING);

            if (claimed == 0) {
                log.info("Skipped concurrent in-flight version id={}", dv.getId());
                continue;
            }

            try {
                boolean objectExists = objectStoreService.objectExists(dv.getObjectKey());

                if (objectExists) {
                    byte[] ciphertext = objectStoreService.getObject(dv.getObjectKey());
                    String actualDigest = encryptionService.computeDigest(ciphertext);

                    if (actualDigest.equalsIgnoreCase(dv.getCiphertextDigest())) {
                        // Integrity verified: complete the commit
                        dv.setState(DocumentState.COMMITTED);
                        documentVersionRepository.save(dv);

                        Document doc = dv.getDocument();
                        if (dv.getVersion() > doc.getCurrentVersion()) {
                            doc.setCurrentVersion(dv.getVersion());
                            doc.setUpdatedAt(Instant.now());
                            documentRepository.save(doc);
                        }

                        // Enqueue outbox event
                        String payload = buildOutboxPayload(doc.getId(), dv.getId(), tenantId, dv.getVersion());
                        outboxRepository.save(OutboxEvent.builder()
                                .tenantId(tenantId)
                                .aggregateType("document")
                                .aggregateId(doc.getId())
                                .eventType("DOCUMENT_COMMITTED")
                                .payload(payload)
                                .build());

                        auditService.record(tenantId, actorId, actorName,
                                "DOCUMENT_VERSION", dv.getId(), "RECOVERY_COMMITTED", "SUCCESS",
                                "recovered verified version=" + dv.getVersion());

                        recoveredCount++;
                        log.info("Successfully recovered and committed version id={}", dv.getId());
                        continue;
                    } else {
                        log.warn("Corrupted ciphertext detected during recovery of version id={}, deleting object", dv.getId());
                    }
                }

                // If object does not exist or failed verification: delete object (idempotent) and mark FAILED
                objectStoreService.deleteObject(dv.getObjectKey());
                objectsDeletedCount++;

                dv.setState(DocumentState.FAILED);
                documentVersionRepository.save(dv);
                failedCount++;

                auditService.record(tenantId, actorId, actorName,
                        "DOCUMENT_VERSION", dv.getId(), "RECOVERY_FAILED", "FAILED",
                        "unrecoverable version marked failed, orphan deleted");

                log.info("Marked stale version id={} as FAILED", dv.getId());

            } catch (Exception e) {
                log.error("Reconciliation error for version id={}", dv.getId(), e);
                dv.setState(DocumentState.FAILED);
                documentVersionRepository.save(dv);
                failedCount++;
            }
        }

        auditService.record(tenantId, actorId, actorName,
                "SYSTEM", null, "RECONCILE",
                "SUCCESS",
                "stale=" + tenantStale.size() + " recovered=" + recoveredCount +
                " failed=" + failedCount + " objectsDeleted=" + objectsDeletedCount);

        return ReconcileResult.builder()
                .staleRecordsFound(tenantStale.size())
                .recovered(recoveredCount)
                .failed(failedCount)
                .objectsDeleted(objectsDeletedCount)
                .build();
    }

    private String buildOutboxPayload(UUID docId, UUID versionId, UUID tenantId, int version) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "documentId", docId.toString(),
                    "versionId", versionId.toString(),
                    "tenantId", tenantId.toString(),
                    "version", version
            ));
        } catch (JsonProcessingException e) {
            return "{\"error\":\"payload serialization failed\"}";
        }
    }
}
