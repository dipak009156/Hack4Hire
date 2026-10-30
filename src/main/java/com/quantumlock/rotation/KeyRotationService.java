package com.quantumlock.rotation;

import com.quantumlock.audit.AuditService;
import com.quantumlock.common.TenantContext;
import com.quantumlock.crypto.EnvelopeEncryptionService;
import com.quantumlock.document.DocumentVersion;
import com.quantumlock.document.DocumentVersionRepository;
import com.quantumlock.kms.KmsService;
import com.quantumlock.kms.WrappedDek;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Performs incremental, resumable KEK rotation for all document version DEKs
 * within a tenant (ADR-002, Invariant I2).
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Create a {@link KeyRotation} job row in PENDING state.</li>
 *   <li>In batches of 50, load COMMITTED versions whose {@code kekVersion = source}.</li>
 *   <li>Unwrap the DEK with the old KEK, immediately re-wrap with the new KEK.</li>
 *   <li>Save the updated {@code wrapped_dek} and {@code kek_version} in one transaction.</li>
 *   <li>Advance the cursor (last version ID) so the job can resume after a crash.</li>
 *   <li>On completion mark the job COMPLETED; on unrecoverable error mark FAILED.</li>
 * </ol>
 *
 * <p>The rotation is tenant-isolated — it only processes versions belonging to the
 * calling tenant. It never touches the plaintext DEK beyond in-memory operations.
 */
@Service
@RequiredArgsConstructor
public class KeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(KeyRotationService.class);
    private static final int BATCH_SIZE = 50;

    private final KeyRotationRepository keyRotationRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final KmsService kmsService;
    private final AuditService auditService;

    // -------------------------------------------------------------------------
    // Start a rotation job
    // -------------------------------------------------------------------------

    @Transactional
    public KeyRotation startRotation(int sourceKekVersion, int targetKekVersion,
                                     UUID actorId, String actorName) {
        UUID tenantId = TenantContext.requireTenantId();

        // Guard: reject if an active rotation already exists for this tenant
        List<KeyRotation> active = keyRotationRepository.findActiveByTenantId(tenantId);
        if (!active.isEmpty()) {
            throw new IllegalStateException(
                    "A rotation job is already active for this tenant: " + active.get(0).getId());
        }

        long total = countVersionsToRotate(tenantId, sourceKekVersion);

        KeyRotation job = KeyRotation.builder()
                .tenantId(tenantId)
                .sourceKekVersion(sourceKekVersion)
                .targetKekVersion(targetKekVersion)
                .status("PENDING")
                .totalDocuments(total)
                .build();

        KeyRotation saved = keyRotationRepository.save(job);

        auditService.record(tenantId, actorId, actorName,
                "KEY_ROTATION", saved.getId(),
                "ROTATION_STARTED", "SUCCESS",
                "source=" + sourceKekVersion + " target=" + targetKekVersion + " total=" + total);

        log.info("Key rotation job created id={} source={} target={} total={}",
                saved.getId(), sourceKekVersion, targetKekVersion, total);

        // Run the rotation synchronously in this transaction scope
        // (for large tenants this would be async/scheduled — sufficient for this implementation)
        runRotation(saved, actorId, actorName);
        return saved;
    }

    // -------------------------------------------------------------------------
    // Get rotation status
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public KeyRotation getStatus(UUID rotationId) {
        UUID tenantId = TenantContext.requireTenantId();
        return keyRotationRepository.findByIdAndTenantId(rotationId, tenantId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Rotation job not found: " + rotationId));
    }

    @Transactional(readOnly = true)
    public int getCurrentKekVersion() {
        return kmsService.currentKekVersion();
    }

    @Transactional(readOnly = true)
    public List<KeyRotation> listRotationHistory() {
        UUID tenantId = TenantContext.requireTenantId();
        return keyRotationRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    // -------------------------------------------------------------------------
    // Core rotation loop
    // -------------------------------------------------------------------------

    @Transactional
    public void runRotation(KeyRotation job, UUID actorId, String actorName) {
        UUID tenantId = job.getTenantId();
        job.setStatus("IN_PROGRESS");
        job.setUpdatedAt(Instant.now());
        keyRotationRepository.save(job);

        try {
            long processed = 0;
            UUID cursor = job.getCursor();

            while (true) {
                List<DocumentVersion> batch = fetchBatch(tenantId, job.getSourceKekVersion(), cursor);
                if (batch.isEmpty()) break;

                for (DocumentVersion dv : batch) {
                    rewrapDek(dv, job.getSourceKekVersion(), job.getTargetKekVersion());
                    cursor = dv.getId();
                    processed++;
                }

                // Persist cursor progress after each batch
                job.setCursor(cursor);
                job.setProcessedDocuments(processed);
                job.setUpdatedAt(Instant.now());
                keyRotationRepository.save(job);

                log.debug("Rotation batch done job={} processed={} total={}",
                        job.getId(), processed, job.getTotalDocuments());
            }

            job.setStatus("COMPLETED");
            job.setProcessedDocuments(processed);
            job.setUpdatedAt(Instant.now());
            keyRotationRepository.save(job);

            auditService.record(tenantId, actorId, actorName,
                    "KEY_ROTATION", job.getId(),
                    "ROTATION_COMPLETED", "SUCCESS",
                    "processed=" + processed);

            log.info("Key rotation completed job={} processed={}", job.getId(), processed);

        } catch (Exception e) {
            log.error("Key rotation failed job={}", job.getId(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setUpdatedAt(Instant.now());
            keyRotationRepository.save(job);

            auditService.record(tenantId, actorId, actorName,
                    "KEY_ROTATION", job.getId(),
                    "ROTATION_FAILED", "FAILED",
                    "error=" + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<DocumentVersion> fetchBatch(UUID tenantId, int sourceKekVersion, UUID cursor) {
        // Fetch COMMITTED versions for this tenant with the old kekVersion, after cursor
        return documentVersionRepository.findCommittedVersionsForRotation(
                tenantId, sourceKekVersion, cursor, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));
    }

    private void rewrapDek(DocumentVersion dv, int sourceKekVersion, int targetKekVersion) {
        byte[] dekBytes = null;
        try {
            // Unwrap DEK with old KEK
            byte[] wrappedOld = Base64.getDecoder().decode(dv.getWrappedDek());
            dekBytes = kmsService.unwrapDek(new WrappedDek(wrappedOld, sourceKekVersion));

            // Re-wrap with new KEK
            WrappedDek newWrapped = kmsService.wrapDekWithVersion(dekBytes, targetKekVersion);
            dv.setWrappedDek(Base64.getEncoder().encodeToString(newWrapped.wrappedBytes()));
            dv.setKekVersion(targetKekVersion);
            documentVersionRepository.save(dv);

        } finally {
            // Zero out plaintext DEK
            if (dekBytes != null) {
                java.util.Arrays.fill(dekBytes, (byte) 0);
            }
        }
    }

    private long countVersionsToRotate(UUID tenantId, int sourceKekVersion) {
        return documentVersionRepository.countCommittedVersionsForRotation(tenantId, sourceKekVersion);
    }
}

