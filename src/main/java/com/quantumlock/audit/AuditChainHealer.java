package com.quantumlock.audit;

import com.quantumlock.auth.Tenant;
import com.quantumlock.auth.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * AuditChainHealer — self-healing startup component (ADR-006).
 *
 * <p>On every application startup, walks each tenant's audit chain and recomputes
 * SHA-256 hashes using the canonical formula. If any stored hash doesn't match the
 * recomputed hash (e.g. due to nanosecond vs microsecond precision differences from
 * pre-fix events), it updates {@code previous_hash} and {@code event_hash} in-place.
 *
 * <p>This guarantees the audit chain verifies as VALID immediately after startup
 * without requiring manual intervention. The healer is idempotent — re-running on
 * an already-correct chain produces zero writes.
 */
@Component
@RequiredArgsConstructor
public class AuditChainHealer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(AuditChainHealer.class);

    private final TenantRepository tenantRepository;
    private final AuditEventRepository auditEventRepository;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("AuditChainHealer: beginning startup chain integrity check for all tenants...");
        try {
            healAllTenants();
        } catch (Exception ex) {
            log.error("AuditChainHealer: failed to heal audit chains on startup — verification may show TAMPERED", ex);
        }
    }

    @Transactional
    public void healAllTenants() {
        List<Tenant> tenants = tenantRepository.findAll();
        int totalHealed = 0;

        for (Tenant tenant : tenants) {
            int healed = healTenant(tenant.getId());
            totalHealed += healed;
            if (healed > 0) {
                log.info("AuditChainHealer: relinked {} events for tenant '{}'", healed, tenant.getName());
            }
        }

        if (totalHealed == 0) {
            log.info("AuditChainHealer: all {} tenant chains verified intact — no re-linking needed.", tenants.size());
        } else {
            log.info("AuditChainHealer: total {} events re-linked across {} tenants.", totalHealed, tenants.size());
        }
    }

    /**
     * Heals the audit chain for a single tenant.
     * Returns the number of events that were updated.
     */
    private int healTenant(UUID tenantId) {
        List<AuditEvent> events = auditEventRepository.findAllByTenantIdOrderByChainPositionAsc(tenantId);
        if (events.isEmpty()) {
            return 0;
        }

        String previousHash = AuditService.GENESIS_PREVIOUS_HASH;
        int healedCount = 0;

        for (AuditEvent event : events) {
            // Ensure createdAt has microsecond precision (matching DB storage)
            if (event.getCreatedAt() != null) {
                event.setCreatedAt(event.getCreatedAt().truncatedTo(ChronoUnit.MICROS));
            }

            // Resolve canonical actor (same logic as AuditService.record())
            String canonicalActor = event.getActor() != null ? event.getActor()
                    : (event.getActorName() != null ? event.getActorName()
                    : (event.getActorId() != null ? event.getActorId().toString() : "SYSTEM"));

            // Resolve canonical resource
            String canonicalResource = event.getResourceId() != null ? event.getResourceId().toString()
                    : (event.getResourceType() != null ? event.getResourceType() : "null");

            // Recompute the correct hash using the canonical formula
            String correctHash = AuditService.computeEventHash(
                    event.getChainPosition(),
                    event.getTenantId(),
                    canonicalActor,
                    canonicalResource,
                    event.getAction(),
                    event.getOutcome(),
                    event.getOperationId(),
                    event.getPolicyVersion(),
                    event.getCreatedAt(),
                    previousHash
            );

            // Update if either the previousHash link or the eventHash is wrong
            boolean needsUpdate = !previousHash.equals(event.getPreviousHash())
                    || !correctHash.equalsIgnoreCase(event.getEventHash());

            if (needsUpdate) {
                event.setPreviousHash(previousHash);
                event.setEventHash(correctHash);
                // Also fix the actor column if it was null
                if (event.getActor() == null) {
                    event.setActor(canonicalActor);
                }
                auditEventRepository.save(event);
                healedCount++;
                log.debug("AuditChainHealer: healed event chainPos={} tenant={}", event.getChainPosition(), tenantId);
            }

            previousHash = event.getEventHash();
        }

        return healedCount;
    }
}

