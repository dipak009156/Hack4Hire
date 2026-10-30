package com.quantumlock.sharing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request body for granting document access to another user.
 */
public record ShareDocumentRequest(
        UUID granteeId,
        List<String> permissions,
        String purpose,
        Instant expiresAt,
        Integer maxDownloads
) {
    public ShareDocumentRequest {
        if (granteeId == null) throw new IllegalArgumentException("granteeId is required");
        if (permissions == null || permissions.isEmpty()) permissions = List.of("READ");
    }
}

