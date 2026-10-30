package com.quantumlock.document;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.UUID;

/**
 * Abstraction over MinIO (AWS SDK v2 S3) with Resilience4j circuit breaker and retry.
 *
 * <p>The bucket is auto-initialized at startup via {@link #ensureBucketExists()}.
 * All operations are annotated with the {@code minio} Resilience4j instance
 * configured in {@code application.yml}.
 */
@Service
public class ObjectStoreService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStoreService.class);

    private final S3Client s3Client;
    private final MinioProperties props;

    public ObjectStoreService(S3Client s3Client, MinioProperties props) {
        this.s3Client = s3Client;
        this.props = props;
        ensureBucketExists();
    }

    /**
     * Builds the deterministic S3 object key for a document version:
     * {@code tenants/{tenantId}/documents/{documentId}/v{version}/{uploadId}}
     */
    public static String buildObjectKey(UUID tenantId, UUID documentId, int version, UUID uploadId) {
        return String.format("tenants/%s/documents/%s/v%d/%s",
                tenantId, documentId, version, uploadId);
    }

    /**
     * Stores encrypted bytes in MinIO.
     *
     * @param objectKey   the full S3 object key
     * @param data        ciphertext bytes to store
     * @param contentType stored as S3 content-type metadata
     */
    @CircuitBreaker(name = "minio")
    @Retry(name = "minio")
    public void putObject(String objectKey, byte[] data, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength((long) data.length)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(data));
        log.debug("Stored object key={} size={}", objectKey, data.length);
    }

    /**
     * Retrieves encrypted bytes from MinIO.
     *
     * @param objectKey the full S3 object key
     * @return raw ciphertext bytes
     */
    @CircuitBreaker(name = "minio")
    @Retry(name = "minio")
    public byte[] getObject(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(props.bucket())
                .key(objectKey)
                .build();

        try (var response = s3Client.getObject(request)) {
            byte[] data = response.readAllBytes();
            log.debug("Retrieved object key={} size={}", objectKey, data.length);
            return data;
        } catch (Exception e) {
            throw new ObjectStoreException("Failed to retrieve object key=" + objectKey, e);
        }
    }

    /**
     * Checks whether an object exists in MinIO.
     */
    @CircuitBreaker(name = "minio")
    @Retry(name = "minio")
    public boolean objectExists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.warn("Error checking existence for key={}: {}", objectKey, e.getMessage());
            return false;
        }
    }

    /**
     * Deletes an object from MinIO. Used by the recovery reconciler to clean up
     * orphaned uploads. S3 delete is idempotent.
     */
    @CircuitBreaker(name = "minio")
    @Retry(name = "minio")
    public void deleteObject(String objectKey) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(objectKey)
                    .build();
            s3Client.deleteObject(request);
            log.info("Deleted object key={}", objectKey);
        } catch (Exception e) {
            log.warn("Could not delete object key={}: {}", objectKey, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Startup initialization
    // -------------------------------------------------------------------------

    private void ensureBucketExists() {
        String bucket = props.bucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("MinIO bucket already exists: {}", bucket);
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created MinIO bucket: {}", bucket);
        } catch (Exception e) {
            log.warn("Could not verify/create MinIO bucket '{}': {}", bucket, e.getMessage());
        }
    }
}
