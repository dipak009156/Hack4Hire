package com.quantumlock.rotation;

/** Request body to start a KEK rotation job. */
public record StartRotationRequest(
        int sourceKekVersion,
        int targetKekVersion
) {}

