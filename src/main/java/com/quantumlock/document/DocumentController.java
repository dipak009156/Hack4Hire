package com.quantumlock.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantumlock.auth.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Document management REST controller.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/documents} — upload new document (Idempotency-Key required)</li>
 *   <li>{@code POST /api/v1/documents/{id}/versions} — upload new version (Idempotency-Key required)</li>
 *   <li>{@code GET  /api/v1/documents/{id}} — get document metadata</li>
 *   <li>{@code GET  /api/v1/documents/{id}/versions} — list all versions</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document upload and retrieval")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final DocumentService documentService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // POST /api/v1/documents — Upload new document
    // -------------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Upload a new document", description = "Encrypts and stores a new document. Idempotency-Key header is mandatory.")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @Valid @RequestBody UploadDocumentRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @AuthenticationPrincipal AppUserDetails principal) {

        requireIdempotencyKey(idempotencyKey);

        String endpoint = "POST /api/v1/documents";
        String payloadDigest = IdempotencyService.digestOf(request.content());

        var idempResult = idempotencyService.getOrCreate(
                principal.getTenantId(), principal.getUserId(),
                idempotencyKey, endpoint, payloadDigest);

        if (idempResult.alreadyProcessed()) {
            // If already processed, wait or return completed result
            var completedOp = idempotencyService.awaitCompletion(idempResult.operation().getId(), 3000);
            if (completedOp.isPresent() && "COMPLETED".equals(completedOp.get().getState()) && completedOp.get().getResponseBody() != null) {
                try {
                    DocumentResponse cached = objectMapper.readValue(
                            completedOp.get().getResponseBody(), DocumentResponse.class);
                    return ResponseEntity.status(HttpStatus.CREATED).body(cached);
                } catch (Exception e) {
                    log.warn("Failed to deserialize cached response: {}", e.getMessage());
                }
            }
        }

        try {
            DocumentResponse response = documentService.uploadNewDocument(request, principal, idempResult.operation().getId());
            try {
                String responseBody = objectMapper.writeValueAsString(response);
                idempotencyService.complete(idempResult.operation().getId(), responseBody, HttpStatus.CREATED.value());
            } catch (Exception e) {
                log.warn("Failed to serialize response for idempotency cache: {}", e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            idempotencyService.fail(idempResult.operation().getId());
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/documents/{id}/versions — Upload new version
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Upload a new document version", description = "Uploads a new encrypted version. Idempotency-Key header is mandatory.")
    public ResponseEntity<DocumentResponse> uploadVersion(
            @PathVariable UUID id,
            @Valid @RequestBody UploadDocumentRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @AuthenticationPrincipal AppUserDetails principal) {

        requireIdempotencyKey(idempotencyKey);

        String endpoint = "POST /api/v1/documents/" + id + "/versions";
        String payloadDigest = IdempotencyService.digestOf(request.content());

        var idempResult = idempotencyService.getOrCreate(
                principal.getTenantId(), principal.getUserId(),
                idempotencyKey, endpoint, payloadDigest);

        if (idempResult.alreadyProcessed()) {
            var completedOp = idempotencyService.awaitCompletion(idempResult.operation().getId(), 3000);
            if (completedOp.isPresent() && "COMPLETED".equals(completedOp.get().getState()) && completedOp.get().getResponseBody() != null) {
                try {
                    DocumentResponse cached = objectMapper.readValue(
                            completedOp.get().getResponseBody(), DocumentResponse.class);
                    return ResponseEntity.status(HttpStatus.CREATED).body(cached);
                } catch (Exception e) {
                    log.warn("Failed to deserialize cached response: {}", e.getMessage());
                }
            }
        }

        try {
            DocumentResponse response = documentService.uploadNewVersion(id, request, principal, idempResult.operation().getId());
            try {
                String responseBody = objectMapper.writeValueAsString(response);
                idempotencyService.complete(idempResult.operation().getId(), responseBody, HttpStatus.CREATED.value());
            } catch (Exception e) {
                log.warn("Failed to serialize response for idempotency cache: {}", e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            idempotencyService.fail(idempResult.operation().getId());
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/documents — List all documents for tenant
    // -------------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    @Operation(summary = "List all documents for current tenant")
    public ResponseEntity<List<DocumentResponse>> listDocuments() {
        return ResponseEntity.ok(documentService.listDocuments());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/documents/{id} — Get document metadata
    // -------------------------------------------------------------------------

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    @Operation(summary = "Get document metadata")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getDocument(id));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/documents/{id}/download — Decrypt & download document
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Download and decrypt document content",
               description = "Checks document ownership or active access grant before decrypting.")
    public ResponseEntity<DownloadResponse> downloadDocument(
            @PathVariable UUID id,
            @RequestParam(name = "version", required = false) Integer version,
            @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(documentService.downloadDocument(id, version, principal));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/documents/{id}/encrypted — Download raw ciphertext / envelope
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/encrypted")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    @Operation(summary = "Download raw ciphertext and envelope metadata",
               description = "Downloads the encrypted ciphertext blob directly from object store along with nonce, wrapped DEK, and KEK version.")
    public ResponseEntity<EncryptedDownloadResponse> downloadEncrypted(
            @PathVariable UUID id,
            @RequestParam(name = "version", required = false) Integer version,
            @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(documentService.downloadEncryptedDocument(id, version, principal));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/documents/{id}/versions — List versions
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    @Operation(summary = "List all versions of a document")
    public ResponseEntity<List<DocumentVersionResponse>> getVersions(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getVersions(id));
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("Idempotency-Key header is required for this operation");
        }
    }
}
