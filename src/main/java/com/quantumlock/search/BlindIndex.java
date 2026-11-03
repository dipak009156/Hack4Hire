package com.quantumlock.search;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "blind_index", indexes = {
        @Index(name = "idx_blind_tenant_token", columnList = "tenant_id, token_hmac"),
        @Index(name = "idx_blind_document", columnList = "document_id"),
        @Index(name = "idx_blind_tenant_doc", columnList = "tenant_id, document_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlindIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "index_key_version", nullable = false)
    @Builder.Default
    private int indexKeyVersion = 1;

    @Column(name = "token_hmac", nullable = false, length = 255)
    private String tokenHmac;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
