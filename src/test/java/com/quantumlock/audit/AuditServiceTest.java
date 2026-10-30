package com.quantumlock.audit;

import com.quantumlock.auth.Tenant;
import com.quantumlock.auth.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditService} — tamper-evident hash chaining and verification.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditEventRepository auditEventRepository;
    @Mock private TenantRepository tenantRepository;

    private AuditService auditService;

    private final UUID tenantId   = UUID.randomUUID();
    private final UUID actorId    = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditEventRepository, tenantRepository);
        lenient().when(tenantRepository.findByIdForUpdate(tenantId))
                .thenReturn(Optional.of(Tenant.builder().id(tenantId).name("tenant-alpha").build()));
    }

    @Test
    @DisplayName("first event starts at chain_position=1 with previous_hash=GENESIS")
    void firstEvent_genesisPositionAndHash() {
        when(auditEventRepository.findTopByTenantIdOrderByChainPositionDesc(tenantId)).thenReturn(Optional.empty());
        when(auditEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AuditEvent event = auditService.record(tenantId, actorId, "alice",
                "DOCUMENT", resourceId, "UPLOAD", "SUCCESS", "detail");

        assertThat(event.getChainPosition()).isEqualTo(1L);
        assertThat(event.getPreviousHash()).isEqualTo(AuditService.GENESIS_PREVIOUS_HASH);
        assertThat(event.getEventHash()).isNotBlank().hasSize(64);
    }

    @Test
    @DisplayName("second event increments chain_position to 2 and chains previous event's hash")
    void secondEvent_chainsPreviousHashAndPosition() {
        // First event
        when(auditEventRepository.findTopByTenantIdOrderByChainPositionDesc(tenantId)).thenReturn(Optional.empty());
        when(auditEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        AuditEvent first = auditService.record(tenantId, actorId, "alice",
                "DOCUMENT", resourceId, "UPLOAD", "SUCCESS", null);

        // Second event
        when(auditEventRepository.findTopByTenantIdOrderByChainPositionDesc(tenantId)).thenReturn(Optional.of(first));
        AuditEvent second = auditService.record(tenantId, actorId, "alice",
                "DOCUMENT", resourceId, "DOWNLOAD", "SUCCESS", null);

        assertThat(second.getChainPosition()).isEqualTo(2L);
        assertThat(second.getPreviousHash()).isEqualTo(first.getEventHash());
        assertThat(second.getEventHash()).isNotEqualTo(first.getEventHash());
    }

    @Test
    @DisplayName("verifyChain succeeds on an intact multi-event chain")
    void verifyChain_intactChain_returnsValid() {
        List<AuditEvent> chain = createIntactChain(5);
        when(auditEventRepository.findAllByTenantIdOrderByChainPositionAsc(tenantId)).thenReturn(chain);

        AuditService.VerificationResult result = auditService.verifyChain(tenantId);

        assertThat(result.valid()).isTrue();
        assertThat(result.totalEvents()).isEqualTo(5);
        assertThat(result.firstInconsistentEvent()).isNull();
    }

    @Test
    @DisplayName("verifyChain detects modification to actor (non-hash field in older designs, now protected)")
    void verifyChain_tamperedActor_detectedAsFirstInconsistentEvent() {
        List<AuditEvent> chain = createIntactChain(4);

        // Tamper with event at chain position 3: change the actor!
        AuditEvent tamperedEvent = chain.get(2);
        tamperedEvent.setActor("mallory_attacker");
        tamperedEvent.setActorName("mallory_attacker");

        when(auditEventRepository.findAllByTenantIdOrderByChainPositionAsc(tenantId)).thenReturn(chain);

        AuditService.VerificationResult result = auditService.verifyChain(tenantId);

        assertThat(result.valid()).isFalse();
        assertThat(result.firstInconsistentEvent()).isNotNull();
        assertThat(result.firstInconsistentEvent().chainPosition()).isEqualTo(3L);
        assertThat(result.firstInconsistentEvent().reason()).contains("Content tampering detected");
    }

    @Test
    @DisplayName("verifyChain detects broken sequence gap (e.g. deleted audit event)")
    void verifyChain_deletedEvent_detectedAsFirstInconsistentEvent() {
        List<AuditEvent> chain = createIntactChain(4);

        // Simulate deletion of event at chain position 2: chain goes 1 -> 3 -> 4
        chain.remove(1);

        when(auditEventRepository.findAllByTenantIdOrderByChainPositionAsc(tenantId)).thenReturn(chain);

        AuditService.VerificationResult result = auditService.verifyChain(tenantId);

        assertThat(result.valid()).isFalse();
        assertThat(result.firstInconsistentEvent()).isNotNull();
        assertThat(result.firstInconsistentEvent().reason()).contains("Broken chain sequence");
    }

    @Test
    @DisplayName("verifyChain detects broken previousHash linkage")
    void verifyChain_brokenPreviousHash_detected() {
        List<AuditEvent> chain = createIntactChain(3);

        // Corrupt previousHash pointer on event 2
        AuditEvent e2 = chain.get(1);
        e2.setPreviousHash("0000000000000000000000000000000000000000000000000000000000000000");

        when(auditEventRepository.findAllByTenantIdOrderByChainPositionAsc(tenantId)).thenReturn(chain);

        AuditService.VerificationResult result = auditService.verifyChain(tenantId);

        assertThat(result.valid()).isFalse();
        assertThat(result.firstInconsistentEvent().chainPosition()).isEqualTo(2L);
        assertThat(result.firstInconsistentEvent().reason()).contains("Previous hash pointer mismatch");
    }

    // -------------------------------------------------------------------------
    // Helper to generate a valid cryptographic chain
    // -------------------------------------------------------------------------

    private List<AuditEvent> createIntactChain(int count) {
        List<AuditEvent> events = new ArrayList<>();
        String prevHash = AuditService.GENESIS_PREVIOUS_HASH;

        for (int i = 1; i <= count; i++) {
            Instant ts = Instant.parse("2026-10-30T10:00:00Z").plusSeconds(i * 10L);
            String actor = "user-" + i;
            String resource = resourceId.toString();
            String action = "ACTION_" + i;
            String outcome = "SUCCESS";

            String hash = AuditService.computeEventHash(
                    i, tenantId, actor, resource, action, outcome, null, null, ts, prevHash);

            AuditEvent event = AuditEvent.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .chainPosition((long) i)
                    .actor(actor)
                    .actorName(actor)
                    .resourceId(resourceId)
                    .action(action)
                    .outcome(outcome)
                    .previousHash(prevHash)
                    .eventHash(hash)
                    .createdAt(ts)
                    .build();

            events.add(event);
            prevHash = hash;
        }

        return events;
    }
}
