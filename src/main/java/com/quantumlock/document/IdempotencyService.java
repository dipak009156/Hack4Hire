package com.quantumlock.document;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency guard for mutating API endpoints (Invariant I4, Q08).
 *
 * <p>Under concurrent execution, relies on the database unique constraint
 * on {@code (tenant_id, caller_id, idempotency_key, endpoint)}. When a
 * {@link DataIntegrityViolationException} is caught, it falls back to the
 * existing record, guaranteeing that duplicate concurrent requests never fail
 * with 500 or produce duplicate side effects.
 *
 * <p>Payload digest verification prevents callers from reusing an idempotency
 * key with a different request body (returning 400 / IllegalArgumentException).
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository idempotencyRepository;

    public record IdempotencyResult(
            boolean alreadyProcessed,
            IdempotencyOperation operation
    ) {}

    /**
     * Checks for an existing idempotent operation record or safely creates a new PENDING one.
     * Uses REQUIRES_NEW so the operation row is independently committed regardless of the outer tx.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyResult getOrCreate(UUID tenantId, UUID callerId,
                                         String idempotencyKey, String endpoint,
                                         String payloadDigest) {

        Optional<IdempotencyOperation> existing =
                idempotencyRepository.findByTenantIdAndCallerIdAndIdempotencyKeyAndEndpoint(
                        tenantId, callerId, idempotencyKey, endpoint);

        if (existing.isPresent()) {
            return handleExisting(existing.get(), idempotencyKey, payloadDigest);
        }

        try {
            IdempotencyOperation newOp = idempotencyRepository.saveAndFlush(
                    IdempotencyOperation.builder()
                            .tenantId(tenantId)
                            .callerId(callerId)
                            .idempotencyKey(idempotencyKey)
                            .endpoint(endpoint)
                            .payloadDigest(payloadDigest)
                            .state("PENDING")
                            .build());

            return new IdempotencyResult(false, newOp);

        } catch (DataIntegrityViolationException dive) {
            log.info("Concurrent insert race detected for idempotencyKey={}, reading existing record", idempotencyKey);
            IdempotencyOperation raced = idempotencyRepository
                    .findByTenantIdAndCallerIdAndIdempotencyKeyAndEndpoint(tenantId, callerId, idempotencyKey, endpoint)
                    .orElseThrow(() -> new IllegalStateException("Operation must exist after DataIntegrityViolation", dive));

            return handleExisting(raced, idempotencyKey, payloadDigest);
        }
    }

    private IdempotencyResult handleExisting(IdempotencyOperation op, String idempotencyKey, String payloadDigest) {
        if (!MessageDigest.isEqual(
                HexFormat.of().parseHex(op.getPayloadDigest()),
                HexFormat.of().parseHex(payloadDigest))) {
            throw new IllegalArgumentException(
                    "Idempotency key '" + idempotencyKey +
                    "' was previously used with a different request payload");
        }
        log.debug("Idempotency match found key={} state={}", idempotencyKey, op.getState());
        return new IdempotencyResult(true, op);
    }

    /**
     * Waits briefly (up to 5 seconds) if an operation is still PENDING, so parallel retries
     * can return the completed response rather than colliding.
     */
    public Optional<IdempotencyOperation> awaitCompletion(UUID operationId, int maxWaitMillis) {
        long deadline = System.currentTimeMillis() + maxWaitMillis;
        while (System.currentTimeMillis() < deadline) {
            Optional<IdempotencyOperation> op = idempotencyRepository.findById(operationId);
            if (op.isPresent() && "COMPLETED".equals(op.get().getState())) {
                return op;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return idempotencyRepository.findById(operationId);
    }

    /** Marks an operation as successfully completed with the serialized response. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID operationId, String responseBody, int responseStatus) {
        idempotencyRepository.complete(operationId, "COMPLETED", responseBody, responseStatus);
    }

    /** Marks an operation as failed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID operationId) {
        idempotencyRepository.complete(operationId, "FAILED", null, 500);
    }

    /** Computes the SHA-256 digest of a UTF-8 string. */
    public static String digestOf(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
