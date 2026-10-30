package com.quantumlock.document;

import java.util.UUID;

public record DownloadResponse(
        UUID documentId,
        int version,
        String name,
        String contentType,
        String contentBase64,
        long sizeBytes
) {}

