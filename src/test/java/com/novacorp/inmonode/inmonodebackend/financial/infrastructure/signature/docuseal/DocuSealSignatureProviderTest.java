package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.signature.docuseal;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureProviderException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureSheet;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** DocuSeal's API as the spike answered it, served by a mock server. */
class DocuSealSignatureProviderTest {

    private static final String BASE = "http://inmonode-signature:3000";
    private static final String HASH = "a".repeat(64);
    private static final SignatureSheet SHEET = new SignatureSheet(7, "comprador@inmonode.test", "N.° 7",
            "comprador@inmonode.test", "Lote A-01 - Los Pinos", "22/11/2026", HASH);

    private MockRestServiceServer server;
    private DocuSealSignatureProvider provider;

    @BeforeEach
    void docuSeal() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new DocuSealSignatureProvider(builder, BASE + "/", "http://localhost:3000", "test-token", 3,
                "Comprador");
    }

    @Test
    void aNewSubmissionFillsTheSignatureSheetWithoutEmailingAnyone() {
        server.expect(requestTo(BASE + "/api/submitters?external_id=inmonode-contract-7"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Auth-Token", "test-token"))
                .andRespond(withSuccess("{\"data\":[],\"pagination\":{\"count\":0}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/submissions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Auth-Token", "test-token"))
                .andExpect(jsonPath("$.template_id").value(3))
                .andExpect(jsonPath("$.send_email").value(false))
                .andExpect(jsonPath("$.submitters[0].role").value("Comprador"))
                .andExpect(jsonPath("$.submitters[0].email").value("comprador@inmonode.test"))
                .andExpect(jsonPath("$.submitters[0].external_id").value("inmonode-contract-7"))
                .andExpect(jsonPath("$.submitters[0].values.Contrato").value("N.° 7"))
                .andExpect(jsonPath("$.submitters[0].values.Lote").value("Lote A-01 - Los Pinos"))
                .andExpect(jsonPath("$.submitters[0].values.Emitido").value("22/11/2026"))
                .andExpect(jsonPath("$.submitters[0].values['SHA-256']").value(HASH))
                .andExpect(jsonPath("$.submitters[0].metadata.app").value("inmonode"))
                .andRespond(withSuccess("""
                        [{"id":11,"submission_id":42,"slug":"Xy12AbC","external_id":"inmonode-contract-7",\
                        "embed_src":"http://localhost:3000/s/Xy12AbC","status":"awaiting"}]""",
                        MediaType.APPLICATION_JSON));

        var invitation = provider.requestSignature(SHEET);

        assertEquals(42, invitation.submissionId());
        assertEquals("http://localhost:3000/s/Xy12AbC", invitation.signingUrl());
        server.verify();
    }

    @Test
    void aSubmissionCreatedByAnAttemptThatTimedOutIsReusedButADeclinedOneIsNot() {
        server.expect(requestTo(BASE + "/api/submitters?external_id=inmonode-contract-7"))
                .andRespond(withSuccess("""
                        {"data":[{"id":10,"submission_id":41,"slug":"Old","external_id":"inmonode-contract-7",\
                        "status":"declined","declined_at":"2026-11-22T10:00:00Z"},\
                        {"id":11,"submission_id":42,"slug":"Live","external_id":"inmonode-contract-7",\
                        "status":"awaiting","declined_at":null}]}""", MediaType.APPLICATION_JSON));

        var invitation = provider.requestSignature(SHEET);

        assertEquals(42, invitation.submissionId());
        assertEquals("http://localhost:3000/s/Live", invitation.signingUrl());
        server.verify();
    }

    @Test
    void aFailureOfDocuSealNeverEchoesTheToken() {
        server.expect(requestTo(BASE + "/api/submitters?external_id=inmonode-contract-7"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        var failure = assertThrows(SignatureProviderException.class, () -> provider.requestSignature(SHEET));

        assertTrue(failure.getMessage().contains("HTTP 503"), failure.getMessage());
        assertFalse(failure.getMessage().contains("test-token"));
    }

    @Test
    void theSubmissionIsReadBackWithItsSignerAndSignedDocument() {
        server.expect(requestTo(BASE + "/api/submissions/42"))
                .andRespond(withSuccess("""
                        {"id":42,"status":"completed","completed_at":"2026-11-22T21:31:55.166Z",\
                        "submitters":[{"external_id":"inmonode-contract-7","status":"completed"}],\
                        "documents":[{"name":"hoja-de-firma","url":"http://localhost:3000/file/abc/hoja.pdf"}]}""",
                        MediaType.APPLICATION_JSON));

        var submission = provider.fetchSubmission(42);

        assertEquals(42, submission.id());
        assertTrue(submission.isCompleted());
        assertEquals("inmonode-contract-7", submission.externalId());
        assertEquals(Instant.parse("2026-11-22T21:31:55.166Z"), submission.completedAt());
        assertEquals("http://localhost:3000/file/abc/hoja.pdf", submission.documentUrl());
    }

    @Test
    void theSignedDocumentIsDownloadedFromTheHostTheBackendReachesFollowingItsRedirect() {
        var pdf = "%PDF-1.7 hoja firmada".getBytes(StandardCharsets.US_ASCII);
        server.expect(requestTo(BASE + "/file/abc/hoja.pdf"))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI.create("http://localhost:3000/blobs/xyz/hoja.pdf")));
        server.expect(requestTo(BASE + "/blobs/xyz/hoja.pdf"))
                .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));
        var submission = new SignatureSubmission(42, SignatureSubmission.Status.COMPLETED, "inmonode-contract-7", null,
                null, "http://localhost:3000/file/abc/hoja.pdf");

        assertArrayEquals(pdf, provider.downloadSignedDocument(submission));
        server.verify();
    }

    @Test
    void aDocumentThatIsMissingOrNotServedFails() {
        var none = new SignatureSubmission(42, SignatureSubmission.Status.COMPLETED, "inmonode-contract-7", null, null,
                null);
        assertThrows(SignatureProviderException.class, () -> provider.downloadSignedDocument(none));

        server.expect(requestTo(BASE + "/file/abc/hoja.pdf")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE));
        var missing = new SignatureSubmission(42, SignatureSubmission.Status.COMPLETED, "inmonode-contract-7", null,
                null, "http://localhost:3000/file/abc/hoja.pdf");
        assertThrows(SignatureProviderException.class, () -> provider.downloadSignedDocument(missing));
    }
}
