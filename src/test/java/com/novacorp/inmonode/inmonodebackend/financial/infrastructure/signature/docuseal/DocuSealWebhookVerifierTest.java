package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.signature.docuseal;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.InvalidSignatureCallbackException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class DocuSealWebhookVerifierTest {

    private static final String SECRET = "whsec_docuseal-unit-secret";
    private static final Instant NOW = Instant.parse("2026-11-22T21:32:00Z");
    private final DocuSealWebhookVerifier verifier = new DocuSealWebhookVerifier(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aSignedFormCompletedNamesItsSubmissionAndContract() {
        var payload = DocuSealCallbacks.form("form.completed", 42, "inmonode-contract-7", null);

        var event = verifier.verify(payload, DocuSealCallbacks.sign(payload, SECRET, NOW.getEpochSecond() - 30));

        assertEquals("form.completed", event.type());
        assertEquals(SignatureEvent.Kind.COMPLETED, event.kind());
        assertEquals(42L, event.submissionId());
        assertEquals("inmonode-contract-7", event.externalId());
        assertEquals("form.completed:42", event.eventKey());
    }

    @Test
    void submissionEventsAndDeclinesAreReadToo() {
        var completed = DocuSealCallbacks.submission("submission.completed", 42, "inmonode-contract-7");
        var event = verifier.verify(completed, DocuSealCallbacks.sign(completed, SECRET, NOW.getEpochSecond()));
        assertEquals(SignatureEvent.Kind.COMPLETED, event.kind());
        assertEquals(42L, event.submissionId());
        assertEquals("inmonode-contract-7", event.externalId());
        assertEquals("submission.completed:42", event.eventKey());

        var declined = DocuSealCallbacks.form("form.declined", 43, "inmonode-contract-8", "No es mi lote");
        var decline = verifier.verify(declined, DocuSealCallbacks.sign(declined, SECRET, NOW.getEpochSecond()));
        assertEquals(SignatureEvent.Kind.DECLINED, decline.kind());
        assertEquals("No es mi lote", decline.declineReason());

        var viewed = DocuSealCallbacks.form("form.viewed", 44, "inmonode-contract-9", null);
        assertEquals(SignatureEvent.Kind.OTHER,
                verifier.verify(viewed, DocuSealCallbacks.sign(viewed, SECRET, NOW.getEpochSecond())).kind());
        var template = "{\"event_type\":\"template.updated\",\"data\":{\"id\":1}}";
        var other = verifier.verify(template, DocuSealCallbacks.sign(template, SECRET, NOW.getEpochSecond()));
        assertEquals(SignatureEvent.Kind.OTHER, other.kind());
        assertNull(other.submissionId());
    }

    @Test
    void aForgedTamperedOldOrMissingSignatureIsRefused() {
        var payload = DocuSealCallbacks.form("form.completed", 42, "inmonode-contract-7", null);
        var now = NOW.getEpochSecond();

        assertInvalid(payload, DocuSealCallbacks.sign(payload, "whsec_otro-secreto", now));
        assertInvalid(payload.replace("\"submission_id\":42", "\"submission_id\":43"),
                DocuSealCallbacks.sign(payload, SECRET, now));
        assertInvalid(payload, DocuSealCallbacks.sign(payload, SECRET, now - 301));
        assertInvalid(payload, DocuSealCallbacks.sign(payload, SECRET, now + 301));
        assertInvalid(payload, null);
        assertInvalid(payload, "");
        assertInvalid(payload, "sin-punto");
        assertInvalid(payload, "abc." + "0".repeat(64));
        assertInvalid("not json", DocuSealCallbacks.sign("not json", SECRET, now));
        assertInvalid("{\"data\":{}}", DocuSealCallbacks.sign("{\"data\":{}}", SECRET, now));
    }

    @Test
    void withoutItsSecretNothingIsTrusted() {
        var unconfigured = new DocuSealWebhookVerifier("", Clock.fixed(NOW, ZoneOffset.UTC));
        var payload = DocuSealCallbacks.form("form.completed", 42, "inmonode-contract-7", null);

        assertFalse(unconfigured.isEnabled());
        assertTrue(verifier.isEnabled());
        assertThrows(InvalidSignatureCallbackException.class,
                () -> unconfigured.verify(payload, DocuSealCallbacks.sign(payload, SECRET, NOW.getEpochSecond())));
    }

    private void assertInvalid(String payload, String signature) {
        assertThrows(InvalidSignatureCallbackException.class, () -> verifier.verify(payload, signature));
    }
}
