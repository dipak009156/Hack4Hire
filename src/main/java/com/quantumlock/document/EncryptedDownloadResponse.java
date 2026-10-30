package com.quantumlock.document;

import java.util.UUID;

public record EncryptedDownloadResponse(
        UUID documentId,
        int version,
        String name,
        String contentType,
        String ciphertextBase64,
        String nonce,
        String wrappedDek,
        int kekVersion,
        String ciphertextDigest,
        long sizeBytes
) {}

