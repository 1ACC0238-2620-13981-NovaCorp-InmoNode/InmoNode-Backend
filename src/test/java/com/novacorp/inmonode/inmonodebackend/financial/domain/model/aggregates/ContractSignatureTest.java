package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractSignature;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractSignatureDelivery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureDeliveryStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignedContractDocument;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** US-56: the signature of a contract and the request that sends it. */
class ContractSignatureTest {

    private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z");
    private static final UUID TRANSACTION = UUID.fromString("e422adfe-e39a-4cb7-a8a5-cadef492cea9");
    private static final UUID DOCUMENT = UUID.fromString("0b6a2f3c-9e10-4c55-9a43-7d0e4a521c1b");
    private static final String HASH = SignedContractDocument.sha256Of("%PDF-1.7 contrato".getBytes(StandardCharsets.US_ASCII));
    private static final SignedContractDocument SIGNED = new SignedContractDocument(
            SignedContractDocument.objectKeyFor(TRANSACTION, DOCUMENT), 21_898,
            SignedContractDocument.sha256Of("%PDF-1.7 hoja firmada".getBytes(StandardCharsets.US_ASCII)));

    @Test
    void theSignatureSheetCitesTheHashOfTheIssuedPdf() {
        assertEquals(64, HASH.length());
        assertTrue(SignedContractDocument.isSha256(HASH));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                SignedContractDocument.sha256Of(new byte[0]));
        assertEquals("contracts/%s/signed/%s.pdf".formatted(TRANSACTION, DOCUMENT), SIGNED.objectKey());
        assertTrue(SignedContractDocument.isPdf("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(SignedContractDocument.isPdf("<html>".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void anIssuedContractIsRequestedSignedOnceAndThenItsLinkIsGone() {
        var contract = issued(HASH);
        assertTrue(contract.canBeSentToSignature());

        assertTrue(contract.requestSignature(7, "https://firma.test/s/abc", NOW));
        assertFalse(contract.requestSignature(7, "https://firma.test/s/abc", NOW), "the same submission changes nothing");
        assertEquals(ContractStatus.SIGNATURE_REQUESTED, contract.getStatus());
        assertTrue(contract.isAwaitingSignature());
        assertEquals(7L, contract.getSignatureSubmissionId());
        assertEquals("https://firma.test/s/abc", contract.getSigningUrl());
        assertThrows(IllegalStateException.class, () -> contract.requestSignature(8, "https://firma.test/s/x", NOW));

        assertThrows(IllegalStateException.class, () -> contract.registerSignature(8, SIGNED, NOW),
                "another submission cannot sign it");
        var signedAt = NOW.plusSeconds(90);
        assertTrue(contract.registerSignature(7, SIGNED, signedAt));
        assertFalse(contract.registerSignature(7, SIGNED, signedAt.plusSeconds(5)), "a repeated callback");
        assertEquals(ContractStatus.SIGNED, contract.getStatus());
        assertEquals(signedAt, contract.getSignedAt());
        assertEquals(SIGNED, contract.getSignedDocument());
        assertNull(contract.getSigningUrl());
        assertFalse(contract.canBeSentToSignature());
        assertThrows(IllegalStateException.class, () -> contract.declineSignature(7, "no", NOW));
        assertThrows(IllegalStateException.class, () -> contract.requestSignature(9, "https://firma.test/s/y", NOW));
    }

    @Test
    void aDeclinedContractCanBeSentAgainWithANewSubmission() {
        var contract = issued(HASH);
        contract.requestSignature(7, "https://firma.test/s/abc", NOW);

        assertTrue(contract.declineSignature(7, "  El lote no es el que elegí  ", NOW));
        assertFalse(contract.declineSignature(7, "otra vez", NOW));
        assertEquals(ContractStatus.SIGNATURE_DECLINED, contract.getStatus());
        assertEquals("El lote no es el que elegí", contract.getSignatureDeclineReason());
        assertNull(contract.getSigningUrl());
        assertThrows(IllegalStateException.class, () -> contract.registerSignature(7, SIGNED, NOW));
        assertThrows(IllegalStateException.class, () -> contract.requestSignature(7, "https://firma.test/s/abc", NOW),
                "a declined submission is not reused");

        assertTrue(contract.requestSignature(8, "https://firma.test/s/def", NOW.plusSeconds(60)));
        assertEquals(ContractStatus.SIGNATURE_REQUESTED, contract.getStatus());
        assertNull(contract.getSignatureDeclinedAt());
        assertNull(contract.getSignatureDeclineReason());
        assertTrue(contract.registerSignature(8, SIGNED, NOW.plusSeconds(120)));
    }

    @Test
    void aContractIssuedBeforeTheSignatureNeedsItsHashFirst() {
        var contract = issued(null);
        assertThrows(IllegalStateException.class, () -> contract.requestSignature(7, "https://firma.test/s/abc", NOW));

        contract.attachDocumentSha256(HASH);
        contract.attachDocumentSha256(HASH);
        assertThrows(IllegalArgumentException.class, () -> contract.attachDocumentSha256("0".repeat(64)),
                "the PDF changed since it was issued");
        assertThrows(IllegalArgumentException.class, () -> contract.attachDocumentSha256("ABC"));
        assertTrue(contract.requestSignature(7, "https://firma.test/s/abc", NOW));
        assertThrows(IllegalArgumentException.class, () -> issued(HASH).requestSignature(7, " ", NOW));
    }

    @Test
    void theSignatureSurvivesBeingStoredAndRestored() {
        var contract = issued(HASH);
        contract.requestSignature(7, "https://firma.test/s/abc", NOW);
        contract.registerSignature(7, SIGNED, NOW.plusSeconds(90));

        var restored = Contract.restore(1L, 5L, TRANSACTION, 41L, 3L, DOCUMENT, "contracts/a.pdf", 2048,
                contract.getStatus(), NOW, 77L, null, null, contract.getSignature());

        assertEquals(contract.getSignature(), restored.getSignature());
        assertTrue(restored.isSigned());
    }

    @Test
    void aFailedRequestWaitsLongerEachTimeAndFailsAfterEightAttempts() {
        var delivery = ContractSignatureDelivery.queue(1L, NOW);
        assertTrue(delivery.isDue(NOW));
        assertEquals(Duration.ofMinutes(1), ContractSignatureDelivery.waitAfter(1));
        assertEquals(Duration.ofMinutes(32), ContractSignatureDelivery.waitAfter(6));
        assertEquals(Duration.ofHours(1), ContractSignatureDelivery.waitAfter(7));
        assertThrows(IllegalArgumentException.class, () -> ContractSignatureDelivery.waitAfter(0));

        var at = NOW;
        var waits = new long[ContractSignatureDelivery.MAX_ATTEMPTS - 1];
        for (var attempt = 1; attempt < ContractSignatureDelivery.MAX_ATTEMPTS; attempt++) {
            delivery = delivery.failedAttempt("DocuSeal could not create the submission: HTTP 503", at);
            assertEquals(SignatureDeliveryStatus.PENDING, delivery.status());
            assertEquals(attempt, delivery.attempts());
            assertFalse(delivery.isDue(at), "it waits before the next attempt");
            waits[attempt - 1] = Duration.between(at, delivery.nextAttemptAt()).toMinutes();
            at = delivery.nextAttemptAt();
            assertTrue(delivery.isDue(at));
        }
        assertArrayEquals(new long[]{1, 2, 4, 8, 16, 32, 60}, waits);

        delivery = delivery.failedAttempt("x".repeat(600), at);
        assertEquals(SignatureDeliveryStatus.FAILED, delivery.status());
        assertTrue(delivery.isExhausted());
        assertEquals(8, delivery.attempts());
        assertEquals(500, delivery.lastError().length());
        assertFalse(delivery.isDue(at.plus(Duration.ofDays(1))));
        var failed = delivery;
        assertThrows(IllegalStateException.class, () -> failed.failedAttempt("again", NOW));
        assertThrows(IllegalStateException.class, () -> failed.sent(NOW));

        var sent = ContractSignatureDelivery.queue(1L, NOW).sent(NOW);
        assertEquals(SignatureDeliveryStatus.SENT, sent.status());
        assertEquals(NOW, sent.sentAt());
        assertNull(sent.lastError());
    }

    private static Contract issued(String hash) {
        var signature = new ContractSignature(hash, null, null, null, null, null, null, null);
        return Contract.restore(1L, 5L, TRANSACTION, 41L, 3L, DOCUMENT, "contracts/a.pdf", 2048, ContractStatus.ISSUED,
                NOW, 77L, null, null, signature);
    }
}
