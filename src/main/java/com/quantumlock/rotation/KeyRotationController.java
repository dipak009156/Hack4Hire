package com.quantumlock.rotation;

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

import java.util.UUID;

/**
 * REST controller for KEK rotation jobs (Phase 5).
 *
 * <ul>
 *   <li>{@code POST /api/v1/rotation/start} — Start a new rotation job (Idempotency-Key required, ADMIN only)</li>
 *   <li>{@code GET /api/v1/rotation/{id}/status} — Poll job status</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/rotation")
@RequiredArgsConstructor
@Tag(name = "Key Rotation", description = "DEK re-wrapping key rotation jobs")
public class KeyRotationController {

    private static final Logger log = LoggerFactory.getLogger(KeyRotationController.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final KeyRotationService keyRotationService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping("/start")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Start a DEK re-wrap rotation job",
               description = "Re-wraps all document DEKs from sourceKekVersion to targetKekVersion. Idempotency-Key required.")
    public ResponseEntity<RotationStatusResponse> startRotation(
            @RequestBody StartRotationRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @AuthenticationPrincipal AppUserDetails principal) {

        requireIdempotencyKey(idempotencyKey);

        String endpoint = "POST /api/v1/rotation/start";
        String payloadDigest = IdempotencyService.digestOf(
                request.sourceKekVersion() + ":" + request.targetKekVersion());

        var idempResult = idempotencyService.getOrCreate(
                principal.getTenantId(), principal.getUserId(),
                idempotencyKey, endpoint, payloadDigest);

        if (idempResult.alreadyProcessed()) {
            var completedOp = idempotencyService.awaitCompletion(idempResult.operation().getId(), 3000);
            if (completedOp.isPresent() && "COMPLETED".equals(completedOp.get().getState())
                    && completedOp.get().getResponseBody() != null) {
                try {
                    RotationStatusResponse cached = objectMapper.readValue(
                            completedOp.get().getResponseBody(), RotationStatusResponse.class);
                    return ResponseEntity.status(HttpStatus.CREATED).body(cached);
                } catch (Exception e) {
                    log.warn("Failed to deserialize cached rotation response: {}", e.getMessage());
                }
            }
        }

        try {
            KeyRotation job = keyRotationService.startRotation(
                    request.sourceKekVersion(), request.targetKekVersion(),
                    principal.getUserId(), principal.getUsername());

            RotationStatusResponse response = toResponse(job);
            try {
                idempotencyService.complete(idempResult.operation().getId(),
                        objectMapper.writeValueAsString(response), 201);
            } catch (Exception e) {
                log.warn("Failed to cache rotation response: {}", e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            idempotencyService.fail(idempResult.operation().getId());
            throw e;
        }
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get rotation job status",
               description = "Returns progress of a running or completed rotation job.")
    public ResponseEntity<RotationStatusResponse> getStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails principal) {

        KeyRotation job = keyRotationService.getStatus(id);
        return ResponseEntity.ok(toResponse(job));
    }

    private RotationStatusResponse toResponse(KeyRotation job) {
        return new RotationStatusResponse(
                job.getId(),
                job.getTenantId(),
                job.getSourceKekVersion(),
                job.getTargetKekVersion(),
                job.getStatus(),
                job.getTotalDocuments(),
                job.getProcessedDocuments(),
                job.getErrorMessage()
        );
    }

    private void requireIdempotencyKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("Idempotency-Key header is required for this operation");
        }
    }
}

