package com.quantumlock.audit;

import com.quantumlock.auth.TenantRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists tamper-evident audit events with cryptographic hash chaining (ADR-006 / Invariant I6).
 *
 * <p>Each event's hash is computed as:
 * <pre>
 * SHA-256(chain_position|tenantId|actor|resource|action|outcome|operationId|policyVersion|timestamp|previousHash)
 * </pre>
 *
 * <p>Per-tenant serialization is enforced via a pessimistic write lock (SELECT FOR UPDATE on tenant)
 * ensuring that concurrent requests never race or fork the audit chain.
 * Chain positions are assigned monotonically (1, 2, 3...) under the tenant lock.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    public static final String GENESIS_PREVIOUS_HASH = "GENESIS";

    private final AuditEventRepository auditEventRepository;
    private final TenantRepository tenantRepository;

    @Builder
    public record VerificationResult(
            boolean valid,
            int totalEvents,
            InconsistentEventDetail firstInconsistentEvent,
            String message
    ) {}

    @Builder
    public record InconsistentEventDetail(
            Long chainPosition,
            UUID eventId,
            String action,
            String expectedHash,
            String actualHash,
            String expectedPreviousHash,
            String actualPreviousHash,
            String reason
    ) {}

    /**
     * Records an audit event with per-tenant serialization and strict hash chaining.
     */
    @Transactional
    public AuditEvent record(
            UUID tenantId,
            UUID actorId,
            String actorName,
            String resourceType,
            UUID resourceId,
            String action,
            String outcome,
            String detail,
            UUID operationId,
            Long policyVersion) {

        // 1. Serialize per-tenant appends via pessimistic lock on Tenant
        tenantRepository.findByIdForUpdate(tenantId);

        // 2. Query latest event for this tenant ordered by chain_position
        Optional<AuditEvent> latest = auditEventRepository.findTopByTenantIdOrderByChainPositionDesc(tenantId);

        long chainPosition = latest.map(e -> e.getChainPosition() + 1).orElse(1L);
        String previousHash = latest.map(AuditEvent::getEventHash).orElse(GENESIS_PREVIOUS_HASH);

        String canonicalActor = actorName != null ? actorName : (actorId != null ? actorId.toString() : "SYSTEM");
        String canonicalResource = resourceId != null ? resourceId.toString() : (resourceType != null ? resourceType : "null");
        Instant now = Instant.now();

        String eventHash = computeEventHash(
                chainPosition,
                tenantId,
                canonicalActor,
                canonicalResource,
                action,
                outcome,
                operationId,
                policyVersion,
                now,
                previousHash
        );

        AuditEvent event = AuditEvent.builder()
                .tenantId(tenantId)
                .chainPosition(chainPosition)
                .actorId(actorId)
                .actorName(actorName)
                .actor(canonicalActor)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .action(action)
                .operationId(operationId)
                .policyVersion(policyVersion)
                .outcome(outcome)
                .detail(detail)
                .previousHash(previousHash)
                .eventHash(eventHash)
                .createdAt(now)
                .build();

        AuditEvent saved = auditEventRepository.save(event);
        log.debug("Audit recorded tenant={} chainPos={} action={} hash={}",
                tenantId, chainPosition, action, eventHash.substring(0, 12));
        return saved;
    }

    /**
     * Overload for callers who don't provide operationId or policyVersion.
     */
    @Transactional
    public AuditEvent record(
            UUID tenantId,
            UUID actorId,
            String actorName,
            String resourceType,
            UUID resourceId,
            String action,
            String outcome,
            String detail) {
        return record(tenantId, actorId, actorName, resourceType, resourceId, action, outcome, detail, null, null);
    }

    /**
     * Verifies the audit chain integrity for a tenant, walking from chainPosition 1 onwards.
     * Returns the exact FIRST inconsistent event found, or a valid result if all match.
     */
    @Transactional(readOnly = true)
    public VerificationResult verifyChain(UUID tenantId) {
        List<AuditEvent> events = auditEventRepository.findAllByTenantIdOrderByChainPositionAsc(tenantId);

        if (events.isEmpty()) {
            return VerificationResult.builder()
                    .valid(true)
                    .totalEvents(0)
                    .message("No audit events recorded for tenant")
                    .build();
        }

        String expectedPreviousHash = GENESIS_PREVIOUS_HASH;
        long expectedPosition = 1L;

        for (AuditEvent event : events) {
            // Check 1: Monotonic sequence without gaps
            if (event.getChainPosition() == null || event.getChainPosition() != expectedPosition) {
                return VerificationResult.builder()
                        .valid(false)
                        .totalEvents(events.size())
                        .firstInconsistentEvent(InconsistentEventDetail.builder()
                                .chainPosition(event.getChainPosition())
                                .eventId(event.getId())
                                .action(event.getAction())
                                .reason("Broken chain sequence: expected chain position " + expectedPosition +
                                        " but found " + event.getChainPosition())
                                .build())
                        .message("Sequence gap or reordering detected")
                        .build();
            }

            // Check 2: Link continuity (previous_hash pointer)
            if (!expectedPreviousHash.equals(event.getPreviousHash())) {
                return VerificationResult.builder()
                        .valid(false)
                        .totalEvents(events.size())
                        .firstInconsistentEvent(InconsistentEventDetail.builder()
                                .chainPosition(event.getChainPosition())
                                .eventId(event.getId())
                                .action(event.getAction())
                                .expectedPreviousHash(expectedPreviousHash)
                                .actualPreviousHash(event.getPreviousHash())
                                .reason("Previous hash pointer mismatch: expected " + expectedPreviousHash +
                                        " but found " + event.getPreviousHash())
                                .build())
                        .message("Chain linkage broken")
                        .build();
            }

            // Check 3: Cryptographic hash recomputation over all protected fields
            String canonicalActor = event.getActor() != null ? event.getActor() :
                    (event.getActorName() != null ? event.getActorName() :
                            (event.getActorId() != null ? event.getActorId().toString() : "SYSTEM"));
            String canonicalResource = event.getResourceId() != null ? event.getResourceId().toString() :
                    (event.getResourceType() != null ? event.getResourceType() : "null");

            String recomputedHash = computeEventHash(
                    event.getChainPosition(),
                    event.getTenantId(),
                    canonicalActor,
                    canonicalResource,
                    event.getAction(),
                    event.getOutcome(),
                    event.getOperationId(),
                    event.getPolicyVersion(),
                    event.getCreatedAt(),
                    event.getPreviousHash()
            );

            if (!recomputedHash.equalsIgnoreCase(event.getEventHash())) {
                return VerificationResult.builder()
                        .valid(false)
                        .totalEvents(events.size())
                        .firstInconsistentEvent(InconsistentEventDetail.builder()
                                .chainPosition(event.getChainPosition())
                                .eventId(event.getId())
                                .action(event.getAction())
                                .expectedHash(recomputedHash)
                                .actualHash(event.getEventHash())
                                .reason("Content tampering detected: stored hash does not match recomputed hash across event fields")
                                .build())
                        .message("Tampering detected at chain position " + event.getChainPosition())
                        .build();
            }

            expectedPreviousHash = event.getEventHash();
            expectedPosition++;
        }

        return VerificationResult.builder()
                .valid(true)
                .totalEvents(events.size())
                .message("Audit chain verified successfully: " + events.size() + " events verified")
                .build();
    }

    /**
     * Canonical hash computation covering all fields specified in prompt and brief:
     * SHA-256(chain_position|tenantId|actor|resource|action|outcome|operationId|policyVersion|timestamp|previousHash)
     */
    public static String computeEventHash(
            long chainPosition,
            UUID tenantId,
            String actor,
            String resource,
            String action,
            String outcome,
            UUID operationId,
            Long policyVersion,
            Instant timestamp,
            String previousHash) {

        try {
            String raw = String.join("|",
                    String.valueOf(chainPosition),
                    tenantId != null ? tenantId.toString() : "null",
                    actor != null ? actor : "null",
                    resource != null ? resource : "null",
                    action != null ? action : "null",
                    outcome != null ? outcome : "null",
                    operationId != null ? operationId.toString() : "null",
                    policyVersion != null ? policyVersion.toString() : "null",
                    timestamp != null ? timestamp.toString() : "null",
                    previousHash != null ? previousHash : GENESIS_PREVIOUS_HASH
            );
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
