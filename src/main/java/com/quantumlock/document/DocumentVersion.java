package com.quantumlock.document;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private int version;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "ciphertext_digest", nullable = false, length = 255)
    private String ciphertextDigest;

    @Column(nullable = false, length = 255)
    private String nonce;

    @Column(name = "wrapped_dek", nullable = false)
    private String wrappedDek;

    @Column(name = "kek_version", nullable = false)
    private int kekVersion;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String algorithm = "AES-256-GCM";

    @Column(name = "auth_tag_length", nullable = false)
    @Builder.Default
    private int authTagLength = 128;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private DocumentState state = DocumentState.PENDING;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

