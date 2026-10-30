package com.quantumlock.document;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for a single document version. */
@Builder
public record DocumentVersionResponse(
        UUID id,
        UUID documentId,
        int version,
        String algorithm,
        DocumentState state,
        Long sizeBytes,
        String contentType,
        String ciphertextDigest,
        int kekVersion,
        Instant createdAt
) {}

