package com.quantumlock.rotation;

import java.util.UUID;

/**
 * Response DTO for key rotation job status.
 * Contains all fields needed by the frontend.
 */
public record RotationStatusResponse(
        UUID id,
        UUID tenantId,
        int sourceKekVersion,
        int targetKekVersion,
        String status,
        Long totalDocuments,
        Long processedDocuments,
        String errorMessage
) {}
