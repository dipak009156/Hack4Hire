package com.quantumlock.document;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Configures the AWS SDK v2 {@link S3Client} pointed at the MinIO endpoint.
 *
 * <p>Path-style access is enabled because MinIO requires it.
 * The URL-connection HTTP client is used (no Apache HttpClient dependency).
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    public S3Client s3Client(MinioProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .region(Region.US_EAST_1) // MinIO ignores region; AWS SDK requires one
                .forcePathStyle(true)      // required for MinIO
                .build();
    }
}

