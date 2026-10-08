package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.storage.s3;

import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage.PresignedUpload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Presigned uploads against a real S3-compatible server (US-33): the client uploads straight to the storage with
 * the signed URL, and the storage enforces the signed type and size.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class S3ObjectStorageIntegrationTest {

    private static final String JPEG = "image/jpeg";

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private ObjectStorage objectStorage;

    @Test
    void aClientUploadsWithThePresignedUrlAndTheStoredObjectCanBeDescribed() throws Exception {
        var key = uniqueKey();
        var image = new byte[2048];
        var before = Instant.now();

        var upload = objectStorage.presignUpload(key, JPEG, image.length, Duration.ofMinutes(10));
        var response = put(upload, image, JPEG);

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(upload.expiresAt().isAfter(before.plus(Duration.ofMinutes(9))));
        assertTrue(upload.expiresAt().isBefore(before.plus(Duration.ofMinutes(11))));
        var stored = objectStorage.describe(key).orElseThrow();
        assertEquals(image.length, stored.sizeBytes());
        assertEquals(JPEG, stored.contentType());
    }

    @Test
    void theStorageRejectsAFileLargerThanTheSignedSize() throws Exception {
        var key = uniqueKey();
        var upload = objectStorage.presignUpload(key, JPEG, 1024, Duration.ofMinutes(10));

        var response = put(upload, new byte[4096], JPEG);

        assertEquals(403, response.statusCode(), response.body());
        assertTrue(objectStorage.describe(key).isEmpty());
    }

    @Test
    void theStorageRejectsAContentTypeOtherThanTheSignedOne() throws Exception {
        var key = uniqueKey();
        var upload = objectStorage.presignUpload(key, JPEG, 1024, Duration.ofMinutes(10));

        var response = put(upload, new byte[1024], "application/pdf");

        assertEquals(403, response.statusCode(), response.body());
    }

    @Test
    void nothingIsDescribedWhereNothingWasUploaded() {
        assertTrue(objectStorage.describe(uniqueKey()).isEmpty());
    }

    private HttpResponse<String> put(PresignedUpload upload, byte[] body, String contentType) throws Exception {
        var request = HttpRequest.newBuilder(upload.url())
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String uniqueKey() {
        return "tests/" + UUID.randomUUID() + ".jpg";
    }
}
