package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.storage.s3.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection to the S3 file repository: AWS S3 in production, an S3-compatible server (RustFS) in development
 * and tests.
 *
 * @param endpoint        where the backend reaches the storage; blank for AWS S3
 * @param publicEndpoint  host written into presigned URLs, which clients use from outside the backend's network
 *                        (e.g. {@code http://localhost:9000} with docker compose); blank to use {@code endpoint}
 * @param accessKey       blank to use the default AWS credentials chain (IAM role in production)
 * @param pathStyleAccess {@code true} for S3-compatible servers, which do not resolve bucket subdomains
 * @param createBucket    create the bucket at startup when missing (development and tests only)
 */
@ConfigurationProperties("storage.s3")
public record S3StorageProperties(String endpoint, String publicEndpoint, String region, String bucket,
                                  String accessKey, String secretKey, boolean pathStyleAccess,
                                  boolean createBucket) {
}
