package com.quantumlock.sharing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.document.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * REST controller for document access sharing (Phase 4).
 *
 * <ul>
 *   <li>{@code POST /api/v1/documents/{id}/shares} — Grant access (Idempotency-Key required)</li>
 *   <li>{@code DELETE /api/v1/documents/{id}/shares/{grantId}} — Revoke a grant</li>
 *   <li>{@code GET /api/v1/documents/{id}/shares} — List all grants on document</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/documents/{documentId}/shares")
@RequiredArgsConstructor
@Tag(name = "Sharing", description = "Document access grant management")
public class SharingController {

    private static final Logger log = LoggerFactory.getLogger(SharingController.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final SharingService sharingService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Grant document access to another user",
               description = "Creates a time-limited, download-quota-enforced access grant. Idempotency-Key header is required.")
    public ResponseEntity<AccessGrantResponse> grantAccess(
            @PathVariable UUID documentId,
            @RequestBody ShareDocumentRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @AuthenticationPrincipal AppUserDetails principal) {

        requireIdempotencyKey(idempotencyKey);

        String endpoint = "POST /api/v1/documents/" + documentId + "/shares";
        String payloadDigest = IdempotencyService.digestOf(documentId + ":" + request.granteeId());

        var idempResult = idempotencyService.getOrCreate(
                principal.getTenantId(), principal.getUserId(),
                idempotencyKey, endpoint, payloadDigest);

        if (idempResult.alreadyProcessed()) {
            var completedOp = idempotencyService.awaitCompletion(idempResult.operation().getId(), 2000);
            if (completedOp.isPresent() && "COMPLETED".equals(completedOp.get().getState())
                    && completedOp.get().getResponseBody() != null) {
                try {
                    AccessGrantResponse cached = objectMapper.readValue(
                            completedOp.get().getResponseBody(), AccessGrantResponse.class);
                    return ResponseEntity.status(HttpStatus.CREATED).body(cached);
                } catch (Exception e) {
                    log.warn("Failed to deserialize cached share response: {}", e.getMessage());
                }
            }
        }

        try {
            AccessGrantResponse response = sharingService.grantAccess(documentId, request, principal);
            try {
                String responseBody = objectMapper.writeValueAsString(response);
                idempotencyService.complete(idempResult.operation().getId(), responseBody, 201);
            } catch (Exception e) {
                log.warn("Failed to serialize share response for idempotency cache: {}", e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            idempotencyService.fail(idempResult.operation().getId());
            throw e;
        }
    }

    @DeleteMapping("/{grantId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Revoke an access grant",
               description = "Revokes access for the specified grant. Idempotent — revoking an already-revoked grant is a no-op.")
    public ResponseEntity<Void> revokeAccess(
            @PathVariable UUID documentId,
            @PathVariable UUID grantId,
            @AuthenticationPrincipal AppUserDetails principal) {

        sharingService.revokeAccess(documentId, grantId, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    @Operation(summary = "List all access grants on a document")
    public ResponseEntity<List<AccessGrantResponse>> listGrants(
            @PathVariable UUID documentId) {

        return ResponseEntity.ok(sharingService.listGrants(documentId));
    }

    private void requireIdempotencyKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("Idempotency-Key header is required for this operation");
        }
    }
}

