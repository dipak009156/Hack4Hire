package com.quantumlock.sharing;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request body for granting document access to another user.
 */
public record ShareDocumentRequest(
        @JsonAlias({"recipientId", "grantee_id", "userId"})
        UUID granteeId,

        @JsonAlias({"document_id"})
        UUID documentId,

        List<String> permissions,

        String purpose,

        Instant expiresAt,

        @JsonAlias({"downloadQuota", "quota", "max_downloads"})
        Integer maxDownloads
) {
    public ShareDocumentRequest {
        if (permissions == null || permissions.isEmpty()) {
            permissions = List.of("READ");
        }
    }
}
