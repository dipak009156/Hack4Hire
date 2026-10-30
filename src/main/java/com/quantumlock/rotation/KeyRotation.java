package com.quantumlock.rotation;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks a long-running DEK re-wrap rotation job per tenant (ADR-002).
 *
 * <p>The job re-wraps all document version DEKs from {@code sourceKekVersion}
 * to {@code targetKekVersion} in batches, resuming via a {@code cursor}
 * (last processed document version ID) across restarts.
 */
@Entity
@Table(name = "key_rotation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeyRotation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_kek_version", nullable = false)
    private int sourceKekVersion;

    @Column(name = "target_kek_version", nullable = false)
    private int targetKekVersion;

    /** Last successfully processed document_version.id — used for batch cursor. */
    @Column
    private UUID cursor;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "total_documents")
    @Builder.Default
    private Long totalDocuments = 0L;

    @Column(name = "processed_documents")
    @Builder.Default
    private Long processedDocuments = 0L;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}

