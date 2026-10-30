package com.quantumlock.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantumlock.audit.AuditService;
import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.common.TenantContext;
import com.quantumlock.crypto.EncryptionResult;
import com.quantumlock.crypto.EnvelopeEncryptionService;
import com.quantumlock.outbox.OutboxEvent;
import com.quantumlock.outbox.OutboxRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core document upload and retrieval service implementing ADR-004.
 *
 * <h3>Upload state machine</h3>
 * <ol>
 *   <li>Encrypt plaintext (AES-256-GCM + AESWrap via {@link EnvelopeEncryptionService}).</li>
 *   <li>Insert {@code DocumentVersion} in {@code PENDING} state.</li>
 *   <li>Upload ciphertext to MinIO.</li>
 *   <li>Transition state to {@code OBJECT_STORED}.</li>
 *   <li>In a single atomic transaction:
 *       commit the version ({@code COMMITTED}), update parent document's
 *       {@code current_version}, enqueue outbox event, persist audit event.</li>
 * </ol>
 *
 * <p>If MinIO upload fails, the state transitions to {@code FAILED}.
 * Any stale {@code PENDING} or {@code OBJECT_STORED} uploads older than 5 minutes
 * are safely reconciled by {@link RecoveryService}.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final EnvelopeEncryptionService encryptionService;
    private final ObjectStoreService objectStoreService;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.quantumlock.sharing.AccessGrantRepository accessGrantRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.quantumlock.search.SearchService searchService;

    // -------------------------------------------------------------------------
    // Upload a new document (creates document + version 1)
    // -------------------------------------------------------------------------

    public DocumentResponse uploadNewDocument(UploadDocumentRequest request, AppUserDetails principal, UUID operationId) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID ownerId = principal.getUserId();

        // 1. Create parent document record
        Document doc = createParentDocument(request, tenantId, ownerId);

        // 2. Run the two-phase upload state machine
        DocumentVersion committed = runUploadStateMachine(
                doc, request, tenantId, ownerId, principal.getUsername(), 1, operationId);

        return toDocumentResponse(doc, committed.getVersion());
    }

    public DocumentResponse uploadNewDocument(UploadDocumentRequest request, AppUserDetails principal) {
        return uploadNewDocument(request, principal, null);
    }

    // -------------------------------------------------------------------------
    // Upload a new version of an existing document
    // -------------------------------------------------------------------------

    public DocumentResponse uploadNewVersion(UUID documentId, UploadDocumentRequest request,
                                             AppUserDetails principal, UUID operationId) {
        UUID tenantId = TenantContext.requireTenantId();

        Document doc = documentRepository.findByIdForCurrentTenant(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found"));

        int nextVersion = doc.getCurrentVersion() + 1;

        DocumentVersion committed = runUploadStateMachine(
                doc, request, tenantId, principal.getUserId(), principal.getUsername(), nextVersion, operationId);

        return toDocumentResponse(doc, committed.getVersion());
    }

    public DocumentResponse uploadNewVersion(UUID documentId, UploadDocumentRequest request,
                                             AppUserDetails principal) {
        return uploadNewVersion(documentId, request, principal, null);
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId) {
        UUID tenantId = TenantContext.requireTenantId();
        Document doc = documentRepository.findByIdForCurrentTenant(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found"));
        return toDocumentResponse(doc, doc.getCurrentVersion());
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> getVersions(UUID documentId) {
        UUID tenantId = TenantContext.requireTenantId();
        if (!documentRepository.existsByIdForCurrentTenant(documentId)) {
            throw new EntityNotFoundException("Document not found");
        }
        return documentVersionRepository
                .findAllByDocumentIdAndTenantIdOrderByVersionDesc(documentId, tenantId)
                .stream()
                .map(this::toVersionResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments() {
        return documentRepository.findAllForCurrentTenant()
                .stream()
                .map(doc -> toDocumentResponse(doc, doc.getCurrentVersion()))
                .collect(Collectors.toList());
    }

    @Transactional
    public DownloadResponse downloadDocument(UUID documentId, Integer versionOpt, AppUserDetails principal) {
        UUID tenantId = TenantContext.requireTenantId();
        Document doc = documentRepository.findByIdForCurrentTenant(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found"));

        boolean isOwner = doc.getOwnerId().equals(principal.getUserId());
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isOwner && !isAdmin) {
            if (accessGrantRepository != null) {
                var activeGrants = accessGrantRepository.findActiveGrantsForGrantee(
                        documentId, principal.getUserId(), tenantId, Instant.now());
                if (activeGrants.isEmpty()) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "No active access grant for document: " + documentId);
                }
                var grant = activeGrants.get(0);
                if (grant.getRemainingDownloads() != null) {
                    accessGrantRepository.decrementRemainingDownloads(grant.getId());
                }
            } else {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Access denied to document: " + documentId);
            }
        }

        int version = (versionOpt != null && versionOpt > 0) ? versionOpt : doc.getCurrentVersion();
        DocumentVersion dv = documentVersionRepository
                .findByDocumentIdAndTenantIdAndVersion(documentId, tenantId, version)
                .orElseThrow(() -> new EntityNotFoundException("Document version not found"));

        byte[] ciphertext = objectStoreService.getObject(dv.getObjectKey());
        byte[] nonce = Base64.getDecoder().decode(dv.getNonce());
        byte[] wrappedDek = Base64.getDecoder().decode(dv.getWrappedDek());

        byte[] plaintext = encryptionService.decrypt(
                ciphertext,
                nonce,
                wrappedDek,
                dv.getKekVersion(),
                dv.getCiphertextDigest(),
                tenantId,
                documentId,
                version
        );

        auditService.record(tenantId, principal.getUserId(), principal.getUsername(),
                "DOCUMENT", documentId, "DOWNLOAD", "SUCCESS",
                "version=" + version);

        return new DownloadResponse(
                doc.getId(),
                version,
                doc.getName(),
                dv.getContentType(),
                Base64.getEncoder().encodeToString(plaintext),
                plaintext.length
        );
    }

    // -------------------------------------------------------------------------
    // Two-Phase Upload State Machine
    // -------------------------------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Document createParentDocument(UploadDocumentRequest request, UUID tenantId, UUID ownerId) {
        return documentRepository.save(Document.builder()
                .tenantId(tenantId)
                .ownerId(ownerId)
                .name(request.name())
                .classification(request.classification())
                .currentVersion(0)
                .build());
    }

    private DocumentVersion runUploadStateMachine(
            Document doc, UploadDocumentRequest request,
            UUID tenantId, UUID ownerId, String actorName, int version, UUID operationId) {

        byte[] plaintext = Base64.getDecoder().decode(request.content());

        // Step 1: Encrypt plaintext
        EncryptionResult enc = encryptionService.encrypt(plaintext, tenantId, doc.getId(), version);

        // Step 2: Persist PENDING row
        String objectKey = ObjectStoreService.buildObjectKey(tenantId, doc.getId(), version, UUID.randomUUID());
        DocumentVersion dv = persistPendingVersion(doc, tenantId, version, objectKey, enc, plaintext.length, request.contentType());
        log.info("Document version recorded state=PENDING docId={} version={}", doc.getId(), version);

        // Step 3: Upload ciphertext to MinIO
        try {
            objectStoreService.putObject(dv.getObjectKey(), enc.ciphertext(), "application/octet-stream");
            // Step 4: Record OBJECT_STORED immediately after MinIO put succeeds
            recordObjectStored(dv);
            log.info("Object stored state=OBJECT_STORED docId={} version={}", doc.getId(), version);
        } catch (Exception e) {
            recordFailed(dv);
            log.error("MinIO upload failed docId={} version={}", doc.getId(), version, e);
            throw new ObjectStoreException("Failed to upload document to object store", e);
        }

        // Step 5: Atomic commit tx: COMMITTED + parent doc current_version + outbox + audit
        commitVersion(dv, doc, tenantId, ownerId, actorName, version, operationId);

        // Step 6: Blind-index indexing (best effort)
        try {
            if (searchService != null) {
                searchService.indexDocument(doc.getId(), new String(plaintext, java.nio.charset.StandardCharsets.UTF_8), doc.getName());
            }
        } catch (Exception e) {
            log.warn("Blind-index indexing failed for docId={}: {}", doc.getId(), e.getMessage());
        }

        return dv;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentVersion persistPendingVersion(Document doc, UUID tenantId, int version,
                                                 String objectKey, EncryptionResult enc,
                                                 long sizeBytes, String contentType) {
        return documentVersionRepository.saveAndFlush(DocumentVersion.builder()
                .document(doc)
                .tenantId(tenantId)
                .version(version)
                .objectKey(objectKey)
                .ciphertextDigest(enc.ciphertextDigest())
                .nonce(Base64.getEncoder().encodeToString(enc.nonce()))
                .wrappedDek(Base64.getEncoder().encodeToString(enc.wrappedDekBytes()))
                .kekVersion(enc.kekVersion())
                .state(DocumentState.PENDING)
                .sizeBytes(sizeBytes)
                .contentType(contentType)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordObjectStored(DocumentVersion dv) {
        dv.setState(DocumentState.OBJECT_STORED);
        documentVersionRepository.saveAndFlush(dv);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(DocumentVersion dv) {
        dv.setState(DocumentState.FAILED);
        documentVersionRepository.saveAndFlush(dv);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commitVersion(DocumentVersion dv, Document doc, UUID tenantId,
                              UUID ownerId, String actorName, int version, UUID operationId) {
        // Transition to COMMITTED
        dv.setState(DocumentState.COMMITTED);
        documentVersionRepository.save(dv);

        // Update parent document's current version
        doc.setCurrentVersion(version);
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        // Enqueue outbox event
        String payload = buildOutboxPayload(doc.getId(), dv.getId(), tenantId, version);
        outboxRepository.save(OutboxEvent.builder()
                .tenantId(tenantId)
                .aggregateType("document")
                .aggregateId(doc.getId())
                .eventType("DOCUMENT_COMMITTED")
                .payload(payload)
                .build());

        // Persist audit event with full hash-chaining
        auditService.record(tenantId, ownerId, actorName,
                "DOCUMENT_VERSION", doc.getId(),
                "UPLOAD_COMMITTED", "SUCCESS",
                "version=" + version + " kekVersion=" + dv.getKekVersion(),
                operationId, null);

        log.info("Document committed state=COMMITTED docId={} version={}", doc.getId(), version);
    }

    // -------------------------------------------------------------------------
    // Mappers
    // -------------------------------------------------------------------------

    private DocumentResponse toDocumentResponse(Document doc, int resolvedVersion) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .tenantId(doc.getTenantId())
                .ownerId(doc.getOwnerId())
                .name(doc.getName())
                .classification(doc.getClassification())
                .currentVersion(resolvedVersion)
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private DocumentVersionResponse toVersionResponse(DocumentVersion dv) {
        return DocumentVersionResponse.builder()
                .id(dv.getId())
                .documentId(dv.getDocument().getId())
                .version(dv.getVersion())
                .algorithm(dv.getAlgorithm())
                .state(dv.getState())
                .sizeBytes(dv.getSizeBytes())
                .contentType(dv.getContentType())
                .ciphertextDigest(dv.getCiphertextDigest())
                .kekVersion(dv.getKekVersion())
                .createdAt(dv.getCreatedAt())
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
