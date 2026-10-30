package com.quantumlock.rotation;

import java.util.UUID;

/** Request body to start a KEK rotation job. */
public record StartRotationRequest(
        int sourceKekVersion,
        int targetKekVersion
) {}

/** Response DTO for rotation job status. */
record RotationStatusResponse(
        UUID id,
        UUID tenantId,
        int sourceKekVersion,
        int targetKekVersion,
        String status,
        long totalDocuments,
        long processedDocuments,
        String errorMessage
) {}

