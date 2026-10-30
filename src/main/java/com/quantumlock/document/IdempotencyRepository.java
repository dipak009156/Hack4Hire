package com.quantumlock.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyOperation, UUID> {

    Optional<IdempotencyOperation> findByTenantIdAndCallerIdAndIdempotencyKeyAndEndpoint(
            UUID tenantId, UUID callerId, String idempotencyKey, String endpoint);

    @Modifying
    @Query("UPDATE IdempotencyOperation o SET o.state = :state, o.responseBody = :body, " +
           "o.responseStatus = :status, o.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE o.id = :id")
    void complete(@Param("id") UUID id,
                  @Param("state") String state,
                  @Param("body") String responseBody,
                  @Param("status") int responseStatus);
}

