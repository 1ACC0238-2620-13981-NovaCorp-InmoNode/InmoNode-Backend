package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.storage.s3;

import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.storage.s3.configuration.S3StorageProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ObjectStorage} on S3 or an S3-compatible server, with the AWS SDK v2.
 */
@Component
public class S3ObjectStorage implements ObjectStorage {

    private static final int NOT_FOUND = 404;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public S3ObjectStorage(S3Client s3Client, S3Presigner s3Presigner, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = properties.bucket();
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes, Duration validity) {
        var putObject = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        var presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(validity)
                .putObjectRequest(putObject)
                .build());
        try {
            return new PresignedUpload(presigned.url().toURI(), presigned.expiration(),
                    Map.of("Content-Type", contentType, "Content-Length", String.valueOf(sizeBytes)));
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("The storage signed an invalid URL", ex);
        }
    }

    @Override
    public Optional<StoredObject> describe(String key) {
        try {
            var head = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
        } catch (S3Exception ex) {
            if (ex.statusCode() == NOT_FOUND) {
                return Optional.empty();
            }
            throw ex;
        }
    }
}
