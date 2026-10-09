package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.auditing;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AuditJsonRedactorTest {
    @Test void nestedSecretsAndPresignedCredentialsAreMasked() {
        var body = """
                {"amount":120,"password":"secret-pass","data":[{"access_token":"sensitive-token",
                "card_number":"4111111111111111","downloadUrl":"https://bucket?secret=abc","documentNumber":"12345678"}],
                "details":"echoed-secret"}
                """;
        var logged = AuditJsonRedactor.serialize(AuditJsonRedactor.redact(body.getBytes(StandardCharsets.UTF_8)));
        for (var secret : new String[]{"secret-pass", "sensitive-token", "4111111111111111", "https://bucket", "12345678", "echoed-secret"})
            assertFalse(logged.contains(secret), logged);
        assertTrue(logged.contains("120"));
        assertTrue(logged.contains("****"));
    }

    @Test void brokenJsonAndOversizedBodiesNeverFallBackToRawLogging() {
        assertFalse(AuditJsonRedactor.serialize(AuditJsonRedactor.redact("password=hidden".getBytes())).contains("hidden"));
        assertTrue(AuditJsonRedactor.serialize(AuditJsonRedactor.redact(new byte[20000])).contains("bodyOmitted"));
    }
}
