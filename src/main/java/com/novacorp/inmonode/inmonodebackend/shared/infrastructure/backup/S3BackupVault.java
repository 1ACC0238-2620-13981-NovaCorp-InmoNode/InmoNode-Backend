package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.backup;

import com.novacorp.inmonode.inmonodebackend.shared.application.backup.BackupVault;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.Base64;

public class S3BackupVault implements BackupVault, AutoCloseable {
    private final S3Client s3;
    private final String bucket, prefix;

    public S3BackupVault(S3Client s3, String bucket, String prefix) {
        if (bucket == null || bucket.isBlank() || prefix == null || prefix.isBlank()
                || !prefix.endsWith("/") || prefix.startsWith("/") || prefix.contains("..")) {
            throw new IllegalArgumentException("An external backup bucket and a dedicated prefix ending in / are required");
        }
        this.s3 = s3; this.bucket = bucket; this.prefix = prefix;
    }

    @Override
    public void uploadVerified(String key, Path compressedDump) {
        if (!key.startsWith("backup-") || !key.endsWith(".sql.gz") || key.contains("/")) throw new IllegalArgumentException("invalid backup key");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(compressedDump), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            var checksum = Base64.getEncoder().encodeToString(digest.digest());
            var objectKey = prefix + key;
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey).contentType("application/gzip")
                    .serverSideEncryption(ServerSideEncryption.AES256).checksumSHA256(checksum).build(),
                    RequestBody.fromFile(compressedDump));
            var stored = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey)
                    .checksumMode(ChecksumMode.ENABLED).build());
            if (stored.contentLength() != Files.size(compressedDump) || !checksum.equals(stored.checksumSHA256())) {
                throw new IllegalStateException("The vault backup failed checksum verification");
            }
        } catch (IOException | NoSuchAlgorithmException e) { throw new IllegalStateException("Could not verify database backup", e); }
    }

    @Override
    public void deleteExpired(Instant cutoff) {
        for (var page : s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())) {
            for (var object : page.contents()) {
                if (!object.key().startsWith(prefix)) continue;
                var name = object.key().substring(prefix.length());
                if (!name.startsWith("backup-") || !name.endsWith(".sql.gz")
                        || name.contains("/") || object.lastModified() == null || !object.lastModified().isBefore(cutoff)) continue;
                s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(object.key()).build());
            }
        }
    }

    @Override
    public void close() { s3.close(); }
}
