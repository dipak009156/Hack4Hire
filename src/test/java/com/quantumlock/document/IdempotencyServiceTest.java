package com.quantumlock.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit and concurrency tests for {@link IdempotencyService} (Invariant I4, Q08).
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    private IdempotencyService idempotencyService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final String idempotencyKey = UUID.randomUUID().toString();
    private final String endpoint = "POST /api/v1/documents";
    private final String payloadDigest = IdempotencyService.digestOf("{\"name\":\"doc.txt\"}");

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(idempotencyRepository);
    }

    @Test
    @DisplayName("new key creates a PENDING operation")
    void newKey_createsPendingOperation() {
        when(idempotencyRepository.findByTenantIdAndCallerIdAndIdempotencyKeyAndEndpoint(tenantId, callerId, idempotencyKey, endpoint))
                .thenReturn(Optional.empty());

        IdempotencyOperation saved = IdempotencyOperation.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .callerId(callerId)
                .idempotencyKey(idempotencyKey)
                .endpoint(endpoint)
                .payloadDigest(payloadDigest)
                .state("PENDING")
                .build();

        when(idempotencyRepository.saveAndFlush(any())).thenReturn(saved);

        IdempotencyService.IdempotencyResult result = idempotencyService.getOrCreate(
                tenantId, callerId, idempotencyKey, endpoint, payloadDigest);

        assertThat(result.alreadyProcessed()).isFalse();
        assertThat(result.operation().getState()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("replay of same key and payload returns alreadyProcessed=true with cached operation")
    void replaySamePayload_returnsExistingOperation() {
        IdempotencyOperation existing = IdempotencyOperation.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .callerId(callerId)
                .idempotencyKey(idempotencyKey)
                .endpoint(endpoint)
                .payloadDigest(payloadDigest)
                .state("COMPLETED")
                .responseBody("{\"id\":\"doc-123\"}")
                .responseStatus(201)
                .build();

        when(idempotencyRepository.findByTenantIdAndCallerIdAndIdempotencyKeyAndEndpoint(tenantId, callerId, idempotencyKey, endpoint))
                .thenReturn(Optional.of(existing));

        IdempotencyService.IdempotencyResult result = idempotencyService.getOrCreate(
                tenantId, callerId, idempotencyKey, endpoint, payloadDigest);

        assertThat(result.alreadyProcessed()).isTrue();
        assertThat(result.operation().getId()).isEqualTo(existing.getId());
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("reuse of key with different payload throws IllegalArgumentException")
    void reuseKey_differentPayload_throwsException() {
        IdempotencyOperation existing = IdempotencyOperation.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .callerId(callerId)
                .idempotencyKey(idempotencyKey)
                .endpoint(endpoint)
                .payloadDigest(payloadDigest)
                .state("COMPLETED")
                .build();

        when(idempotencyRepository.findByTenantIdAndCallerIdAndIdempotencyKeyAndEndpoint(tenantId, callerId, idempotencyKey, endpoint))
                .thenReturn(Optional.of(existing));

        String differentPayloadDigest = IdempotencyService.digestOf("{\"name\":\"completely-different.txt\"}");

        assertThatThrownBy(() -> idempotencyService.getOrCreate(tenantId, callerId, idempotencyKey, endpoint, differentPayloadDigest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("previously used with a different request payload");
    }

    @Test
    @DisplayName("parallel retries: concurrent race caught via DataIntegrityViolationException returns original record")
    void parallelRetries_catchesDuplicateKey_returnsExisting() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        IdempotencyOperation winnerOp = IdempotencyOperation.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .callerId(callerId)
                .idempotencyKey(idempotencyKey)
                .endpoint(endpoint)
                .payloadDigest(payloadDigest)
                .state("PENDING")
                .build();

        AtomicBoolean firstCaller = new AtomicBoolean(true);

        when(idempotencyRepository.findByTenantIdAndCallerIdAndIdempotencyKeyAndEndpoint(tenantId, callerId, idempotencyKey, endpoint))
                .thenAnswer(inv -> {
                    // Before any insert, both threads see empty
                    if (firstCaller.get()) {
                        return Optional.empty();
                    }
                    return Optional.of(winnerOp);
                });

        when(idempotencyRepository.saveAndFlush(any())).thenAnswer(inv -> {
            if (firstCaller.compareAndSet(true, false)) {
                return winnerOp; // winner inserts
            }
            // concurrent duplicate insert throws DataIntegrityViolationException
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        });

        List<Future<IdempotencyService.IdempotencyResult>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                barrier.await(); // ensure exact simultaneous execution
                return idempotencyService.getOrCreate(tenantId, callerId, idempotencyKey, endpoint, payloadDigest);
            }));
        }

        List<IdempotencyService.IdempotencyResult> results = new ArrayList<>();
        for (Future<IdempotencyService.IdempotencyResult> future : futures) {
            results.add(future.get(5, TimeUnit.SECONDS));
        }

        executor.shutdown();

        // Exactly one should be fresh (alreadyProcessed=false) and all others should be duplicates (alreadyProcessed=true)
        long freshCount = results.stream().filter(r -> !r.alreadyProcessed()).count();
        long duplicateCount = results.stream().filter(IdempotencyService.IdempotencyResult::alreadyProcessed).count();

        assertThat(freshCount).isEqualTo(1);
        assertThat(duplicateCount).isEqualTo(threads - 1);

        // All threads received the identical operation record
        for (IdempotencyService.IdempotencyResult r : results) {
            assertThat(r.operation().getId()).isEqualTo(winnerOp.getId());
        }
    }
}
