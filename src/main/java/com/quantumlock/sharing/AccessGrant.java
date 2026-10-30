package com.quantumlock.sharing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents a time-limited, download-quota–enforced access grant from a document owner
 * (grantor) to another user (grantee) within the same tenant (ADR-003).
 *
 * <p>Optimistic concurrency is enforced via {@code row_version} to prevent lost-update
 * on concurrent revocation or download-count decrement.
 */
@Entity
@Table(name = "access_grant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "grantor_id", nullable = false)
    private UUID grantorId;

    @Column(name = "grantee_id", nullable = false)
    private UUID granteeId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "access_grant", joinColumns = @JoinColumn(name = "id"))
    @Column(name = "permissions")
    @Builder.Default
    private List<String> permissions = List.of("READ");

    @Column(length = 255)
    private String purpose;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "max_downloads")
    private Integer maxDownloads;

    @Column(name = "remaining_downloads")
    private Integer remainingDownloads;

    @Column(name = "policy_version")
    private Long policyVersion;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    @Builder.Default
    private Long rowVersion = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Returns true if this grant is still active (not expired, not revoked, downloads remain). */
    public boolean isActive() {
        if (revokedAt != null) return false;
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) return false;
        if (remainingDownloads != null && remainingDownloads <= 0) return false;
        return true;
    }
}

