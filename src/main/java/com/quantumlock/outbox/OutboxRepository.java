package com.quantumlock.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT o FROM OutboxEvent o WHERE o.deliveryStatus = 'PENDING' ORDER BY o.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    @Query(value = "SELECT * FROM outbox WHERE delivery_status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findPendingEventsForUpdateSkipLocked(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.deliveryStatus = 'DELIVERED', o.deliveredAt = CURRENT_TIMESTAMP, " +
           "o.deliveryAttempts = o.deliveryAttempts + 1 WHERE o.id = :id")
    void markDelivered(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.deliveryAttempts = o.deliveryAttempts + 1 WHERE o.id = :id")
    void incrementAttempts(@Param("id") UUID id);
}
