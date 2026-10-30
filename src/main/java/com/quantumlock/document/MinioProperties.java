package com.quantumlock.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code minio.*} properties from application.yml. */
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket
) {}

