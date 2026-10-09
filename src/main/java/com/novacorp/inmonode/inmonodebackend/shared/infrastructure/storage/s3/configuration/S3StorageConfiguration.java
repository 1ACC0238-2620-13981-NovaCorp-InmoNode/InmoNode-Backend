package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.storage.s3.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(S3StorageConfiguration.class);

    /**
     * Checksums only when S3 requires them: the SDK default adds checksum headers that S3-compatible servers and
     * presigned uploads from simple clients do not handle.
     */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(serviceConfiguration(properties))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    /** Signs URLs for the public endpoint, since clients reach the storage from outside the backend's network. */
    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(S3StorageProperties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(serviceConfiguration(properties));
        var endpoint = hasText(properties.publicEndpoint()) ? properties.publicEndpoint() : properties.endpoint();
        if (hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    /**
     * Development and tests: the bucket is created on startup. A storage that is down is logged, not fatal.
     * The flag is read when the application runs, not as a bean condition, so tests can set it at runtime.
     */
    @Bean
    public ApplicationRunner s3BucketInitializer(S3Client s3Client, S3StorageProperties properties) {
        return arguments -> {
            if (!properties.createBucket()) {
                return;
            }
            try {
                s3Client.headBucket(request -> request.bucket(properties.bucket()));
            } catch (NoSuchBucketException missing) {
                s3Client.createBucket(request -> request.bucket(properties.bucket()));
                LOG.info("Created storage bucket {}", properties.bucket());
            } catch (SdkException unavailable) {
                LOG.warn("Storage bucket {} could not be checked: {}", properties.bucket(), unavailable.getMessage());
            }
        };
    }

    private static AwsCredentialsProvider credentials(S3StorageProperties properties) {
        if (hasText(properties.accessKey())) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }

    private static S3Configuration serviceConfiguration(S3StorageProperties properties) {
        return S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess()).build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
