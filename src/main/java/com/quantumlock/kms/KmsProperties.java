package com.quantumlock.kms;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Binds the {@code kms.*} properties from application.yml.
 * <p>
 * Keys are stored as Base64-encoded 256-bit values keyed by version number.
 * The current-version property controls which KEK is used for new wrapping
 * operations; older versions remain available for unwrapping during rotation.
 */
@ConfigurationProperties(prefix = "kms")
public record KmsProperties(
        int currentVersion,
        boolean faultEnabled,
        Map<Integer, String> keys
) {}

