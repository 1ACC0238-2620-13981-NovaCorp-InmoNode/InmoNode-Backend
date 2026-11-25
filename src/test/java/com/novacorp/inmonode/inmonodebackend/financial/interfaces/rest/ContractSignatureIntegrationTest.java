package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureInvitation;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureProvider;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureProviderException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureSheet;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureSubmission;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveVoucherEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.SendDueSignatureRequestsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractSignatureDelivery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureDeliveryStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignedContractDocument;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractSignatureDeliveryRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ContractSignatureCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.signature.docuseal.DocuSealCallbacks;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-56 end to end against a real PostgreSQL and a real S3-compatible storage: issuing queues the contract with the
 * hash of its PDF, the job sends it, and the callback, signed with the test-only secret of the {@code test} profile
 * and verified for real, signs it and sells the lot. Only DocuSeal's API is mocked. Each test uses its own buyer and
 * lot, and pushes back the requests other tests left due, so the job only sends its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class ContractSignatureIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_docuseal-test-only-secret";
    private static final String WEBHOOK = "/api/v1/webhooks/docuseal";
    private static final AtomicLong BUYERS = new AtomicLong(33_000);
    private static final AtomicLong SUBMISSIONS = new AtomicLong(70_000);

    private final HttpClient http = HttpClient.newHttpClient();

    @MockitoBean
    private SignatureProvider signatureProvider;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private ContractSignatureDeliveryRepository deliveries;

    @Autowired
    private ContractSignatureCommandService signatures;

    @Autowired
    private ReservationCommandService reservationCommandService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void docuSealAnswers() {
        reset(signatureProvider);
        when(signatureProvider.isEnabled()).thenReturn(true);
        when(signatureProvider.requestSignature(any())).thenAnswer(invocation -> {
            var submission = SUBMISSIONS.incrementAndGet();
            return new SignatureInvitation(submission, "https://firma.test/s/slug-" + submission);
        });
        jdbc.update("""
                UPDATE financial_document_control.contract_signature_requests
                SET next_attempt_at = now() + interval '1 day' WHERE status = 'PENDING'
                """);
    }

    @Test
    void issuingQueuesTheContractWithTheHashOfItsPdfAndTheJobSendsIt() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot("A-01");
        var issued = issue(buyer, lot);

        var contract = contractRepository.findById(issued.contractId()).orElseThrow();
        assertEquals(ContractStatus.ISSUED, contract.getStatus());
        assertEquals(SignedContractDocument.sha256Of(issued.pdf()), contract.getDocumentSha256());
        assertEquals(SignatureDeliveryStatus.PENDING, deliveries.findByContractId(issued.contractId()).orElseThrow().status());
        contractOf(buyer, issued.transactionId())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.documentSha256").value(contract.getDocumentSha256()))
                .andExpect(jsonPath("$.signingUrl").value(nullValue()));

        assertTrue(signatures.handle(new SendDueSignatureRequestsCommand(20)) >= 1);

        var sheet = ArgumentCaptor.forClass(SignatureSheet.class);
        verify(signatureProvider, atLeastOnce()).requestSignature(sheet.capture());
        var mine = sheet.getAllValues().stream().filter(sent -> sent.contractId() == issued.contractId())
                .findFirst().orElseThrow();
        assertEquals("inmonode-contract-" + issued.contractId(), mine.externalId());
        assertEquals("N.° " + issued.contractId(), mine.contract());
        assertEquals("Lote A-01 - Firma electrónica", mine.lot());
        assertEquals(contract.getDocumentSha256(), mine.documentSha256());
        assertTrue(mine.signerEmail().endsWith("@inmonode.invalid"), "the test buyer has no account");
        var signed = contractRepository.findById(issued.contractId()).orElseThrow();
        assertEquals(ContractStatus.SIGNATURE_REQUESTED, signed.getStatus());
        assertEquals(SignatureDeliveryStatus.SENT, deliveries.findByContractId(issued.contractId()).orElseThrow().status());
        contractOf(buyer, issued.transactionId())
                .andExpect(jsonPath("$.status").value("SIGNATURE_REQUESTED"))
                .andExpect(jsonPath("$.signingUrl").value("https://firma.test/s/slug-" + signed.getSignatureSubmissionId()));
        assertEquals(LotStatus.RESERVED, lotRepository.findById(lot.getId()).orElseThrow().getStatus());

        signatures.handle(new SendDueSignatureRequestsCommand(20));
        verify(signatureProvider, times(1)).requestSignature(any());
    }

    @Test
    void aFailedSendWaitsLongerEachTimeAndAfterEightAttemptsFinanceIsTold() throws Exception {
        var issued = issue(BUYERS.incrementAndGet(), lot("B-01"));
        doThrow(new SignatureProviderException("DocuSeal could not create the submission: HTTP 503"))
                .when(signatureProvider).requestSignature(any());

        var expectedWaits = new long[]{60, 120, 240, 480, 960, 1920, 3600};
        for (var attempt = 1; attempt <= ContractSignatureDelivery.MAX_ATTEMPTS; attempt++) {
            makeDue(issued.contractId());
            var before = Instant.now();
            signatures.handle(new SendDueSignatureRequestsCommand(20));
            var delivery = deliveries.findByContractId(issued.contractId()).orElseThrow();
            assertEquals(attempt, delivery.attempts());
            assertEquals("DocuSeal could not create the submission: HTTP 503", delivery.lastError());
            if (attempt < ContractSignatureDelivery.MAX_ATTEMPTS) {
                assertEquals(SignatureDeliveryStatus.PENDING, delivery.status());
                var wait = Duration.between(before, delivery.nextAttemptAt()).toSeconds();
                assertTrue(Math.abs(wait - expectedWaits[attempt - 1]) <= 5, "attempt %d waits %ds".formatted(attempt, wait));
            } else {
                assertEquals(SignatureDeliveryStatus.FAILED, delivery.status());
            }
        }
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM financial_document_control.notification_outbox
                WHERE event_key LIKE ? AND recipient = '@finance'
                """, Integer.class, "contract-signature-failed:" + issued.contractId() + ":%"));
        makeDue(issued.contractId());
        signatures.handle(new SendDueSignatureRequestsCommand(20));
        assertEquals(8, deliveries.findByContractId(issued.contractId()).orElseThrow().attempts(), "a failed one waits for the back office");

        requestSignature(issued.contractId(), Role.BUYER).andExpect(status().isForbidden());
        requestSignature(issued.contractId(), Role.FINANCE_ADMIN)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deliveryStatus").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.contract.status").value("ISSUED"));

        reset(signatureProvider);
        when(signatureProvider.isEnabled()).thenReturn(true);
        when(signatureProvider.requestSignature(any())).thenReturn(new SignatureInvitation(SUBMISSIONS.incrementAndGet(),
                "https://firma.test/s/after-retry"));
        signatures.handle(new SendDueSignatureRequestsCommand(20));
        requestSignature(issued.contractId(), Role.FINANCE_ADMIN)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.contract.status").value("SIGNATURE_REQUESTED"))
                .andExpect(jsonPath("$.deliveryStatus").value("SENT"));
        requestSignature(999_999L, Role.FINANCE_ADMIN).andExpect(status().isNotFound());
    }

    @Test
    void withoutTheProviderContractsWaitWithoutSpendingAttempts() throws Exception {
        var issued = issue(BUYERS.incrementAndGet(), lot("F-01"));
        when(signatureProvider.isEnabled()).thenReturn(false);
        makeDue(issued.contractId());

        assertEquals(0, signatures.handle(new SendDueSignatureRequestsCommand(20)));

        var delivery = deliveries.findByContractId(issued.contractId()).orElseThrow();
        assertEquals(SignatureDeliveryStatus.PENDING, delivery.status());
        assertEquals(0, delivery.attempts());
        verify(signatureProvider, never()).requestSignature(any());
        assertEquals(ContractStatus.ISSUED, contractRepository.findById(issued.contractId()).orElseThrow().getStatus());
    }

    @Test
    void theSignedCallbackSignsOnceKeepsTheSheetAndSellsTheLot() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var lot = lot("C-01");
        var issued = issue(buyer, lot);
        var submission = sendToSignature(issued.contractId());
        var sheet = ("%PDF-1.7 hoja firmada del contrato " + issued.contractId()).getBytes(StandardCharsets.US_ASCII);
        var completedAt = Instant.parse("2026-11-22T21:31:55.166Z");
        when(signatureProvider.fetchSubmission(submission)).thenReturn(new SignatureSubmission(submission,
                SignatureSubmission.Status.COMPLETED, "inmonode-contract-" + issued.contractId(), completedAt, null,
                "http://docuseal.test/file/signed.pdf"));
        when(signatureProvider.downloadSignedDocument(any())).thenReturn(sheet);
        var completed = DocuSealCallbacks.form("form.completed", submission, "inmonode-contract-" + issued.contractId(), null);

        callback(completed, WEBHOOK_SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventKey").value("form.completed:" + submission))
                .andExpect(jsonPath("$.outcome").value("SIGNED"));

        assertEquals(LotStatus.SOLD, lotRepository.findById(lot.getId()).orElseThrow().getStatus());
        var signed = contractRepository.findById(issued.contractId()).orElseThrow();
        assertEquals(ContractStatus.SIGNED, signed.getStatus());
        assertEquals(completedAt, signed.getSignedAt());
        assertEquals(SignedContractDocument.sha256Of(sheet), signed.getSignedDocument().sha256());
        var answer = contractOf(buyer, issued.transactionId())
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.signedAt").value(completedAt.toString()))
                .andExpect(jsonPath("$.signingUrl").value(nullValue()))
                .andExpect(jsonPath("$.signedDownloadUrl", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        var download = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(answer, "$.signedDownloadUrl"))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, download.statusCode());
        assertArrayEquals(sheet, download.body());

        callback(completed, WEBHOOK_SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SIGNED"));
        callback(DocuSealCallbacks.submission("submission.completed", submission, "inmonode-contract-" + issued.contractId()),
                WEBHOOK_SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));
        verify(signatureProvider, times(1)).downloadSignedDocument(any());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM financial_document_control.notification_outbox
                WHERE event_key = ? AND recipient = '@legal'
                """, Integer.class, "contract-signed:" + issued.contractId()));
        requestSignature(issued.contractId(), Role.FINANCE_ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIGNATURE_CONFLICT"));
    }

    @Test
    void forgedForeignAndUnconfirmedCallbacksChangeNothing() throws Exception {
        var lot = lot("D-01");
        var issued = issue(BUYERS.incrementAndGet(), lot);
        var submission = sendToSignature(issued.contractId());
        var reference = "inmonode-contract-" + issued.contractId();
        var completed = DocuSealCallbacks.form("form.completed", submission, reference, null);

        callback(completed, "whsec_otro-secreto")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(completed))
                .andExpect(status().isBadRequest());
        callback(DocuSealCallbacks.form("form.completed", submission + 500, "otra-app-1", null), WEBHOOK_SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));
        callback(DocuSealCallbacks.form("form.completed", submission + 501, reference, null), WEBHOOK_SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));
        callback(DocuSealCallbacks.form("form.viewed", submission, reference, null), WEBHOOK_SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));

        when(signatureProvider.fetchSubmission(submission)).thenReturn(new SignatureSubmission(submission,
                SignatureSubmission.Status.PENDING, reference, null, null, null));
        callback(DocuSealCallbacks.submission("submission.completed", submission, reference), WEBHOOK_SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED"));

        doThrow(new SignatureProviderException("DocuSeal could not read submission: HTTP 502"))
                .when(signatureProvider).fetchSubmission(submission);
        callback(completed, WEBHOOK_SECRET)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SIGNATURE_PROVIDER_ERROR"));
        verify(signatureProvider, never()).downloadSignedDocument(any());
        assertEquals(ContractStatus.SIGNATURE_REQUESTED,
                contractRepository.findById(issued.contractId()).orElseThrow().getStatus());
        assertEquals(LotStatus.RESERVED, lotRepository.findById(lot.getId()).orElseThrow().getStatus());

        // Once DocuSeal confirms it, the rejected callback, redelivered, signs it: a rejection is not final.
        reset(signatureProvider);
        when(signatureProvider.fetchSubmission(anyLong())).thenReturn(new SignatureSubmission(submission,
                SignatureSubmission.Status.COMPLETED, reference, null, null, "http://docuseal.test/file/signed.pdf"));
        when(signatureProvider.downloadSignedDocument(any())).thenReturn("%PDF-1.7 firmada".getBytes(StandardCharsets.US_ASCII));
        callback(DocuSealCallbacks.submission("submission.completed", submission, reference), WEBHOOK_SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SIGNED"));
        assertEquals(LotStatus.SOLD, lotRepository.findById(lot.getId()).orElseThrow().getStatus());
        assertEquals("SIGNED", jdbc.queryForObject("""
                SELECT outcome FROM financial_document_control.signature_provider_events WHERE event_key = ?
                """, String.class, "submission.completed:" + submission));
        callback(completed, WEBHOOK_SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));
        callback(DocuSealCallbacks.submission("submission.completed", submission, reference), WEBHOOK_SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SIGNED"));
        verify(signatureProvider, times(1)).downloadSignedDocument(any());
    }

    @Test
    void aDeclinedSignatureIsKeptAndTheContractCanBeSentAgain() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var issued = issue(buyer, lot("E-01"));
        var first = sendToSignature(issued.contractId());
        var reference = "inmonode-contract-" + issued.contractId();
        when(signatureProvider.fetchSubmission(first)).thenReturn(new SignatureSubmission(first,
                SignatureSubmission.Status.DECLINED, reference, null, "El lote no es el que elegí", null));

        callback(DocuSealCallbacks.form("form.declined", first, reference, "El lote no es el que elegí"), WEBHOOK_SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DECLINED"));

        var declined = contractRepository.findById(issued.contractId()).orElseThrow();
        assertEquals(ContractStatus.SIGNATURE_DECLINED, declined.getStatus());
        assertEquals("El lote no es el que elegí", declined.getSignatureDeclineReason());
        contractOf(buyer, issued.transactionId())
                .andExpect(jsonPath("$.status").value("SIGNATURE_DECLINED"))
                .andExpect(jsonPath("$.signingUrl").value(nullValue()))
                .andExpect(jsonPath("$.signatureDeclinedAt", notNullValue()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM financial_document_control.notification_outbox
                WHERE event_key = ? AND recipient = '@finance'
                """, Integer.class, "contract-signature-declined:%d:%d".formatted(issued.contractId(), first)));

        requestSignature(issued.contractId(), Role.FINANCE_ADMIN)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.contract.status").value("SIGNATURE_DECLINED"))
                .andExpect(jsonPath("$.contract.signatureDeclineReason").value("El lote no es el que elegí"))
                .andExpect(jsonPath("$.deliveryStatus").value("PENDING"));
        var second = sendToSignature(issued.contractId());
        assertNotEquals(first, second);
        contractOf(buyer, issued.transactionId())
                .andExpect(jsonPath("$.status").value("SIGNATURE_REQUESTED"))
                .andExpect(jsonPath("$.signingUrl", endsWith("slug-" + second)));
    }

    // ---------------------------------------------------------------- helpers

    private record Issued(long contractId, UUID transactionId, byte[] pdf) {
    }

    /** Sends the contract with the job and returns the submission DocuSeal opened. */
    private long sendToSignature(long contractId) {
        makeDue(contractId);
        signatures.handle(new SendDueSignatureRequestsCommand(20));
        var contract = contractRepository.findById(contractId).orElseThrow();
        assertEquals(ContractStatus.SIGNATURE_REQUESTED, contract.getStatus());
        return contract.getSignatureSubmissionId();
    }

    private void makeDue(long contractId) {
        jdbc.update("""
                UPDATE financial_document_control.contract_signature_requests
                SET next_attempt_at = now() - interval '1 second' WHERE contract_id = ? AND status = 'PENDING'
                """, contractId);
    }

    private ResultActions callback(String payload, String secret) throws Exception {
        return mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(payload)
                .header("X-Docuseal-Signature", DocuSealCallbacks.sign(payload, secret, Instant.now().getEpochSecond())));
    }

    private ResultActions requestSignature(long contractId, Role role) throws Exception {
        return mockMvc.perform(post("/api/v1/contracts/{id}/signature-request", contractId)
                .header(HttpHeaders.AUTHORIZATION, bearer(role, 77L)));
    }

    private ResultActions contractOf(long buyer, UUID transactionId) throws Exception {
        return mockMvc.perform(get("/api/v1/reservations/{id}/contract", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)))
                .andExpect(status().isOk());
    }

    /** Quote, separation, voucher, approval and contract, as the buyer and the back office do it. */
    private Issued issue(long buyer, Lot lot) throws Exception {
        var quotation = mockMvc.perform(post("/api/v1/lots/{lotId}/quotations", lot.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialPayment\": 9000, \"termMonths\": 12}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var separation = mockMvc.perform(post("/api/v1/lots/{lotId}/separation-requests", lot.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quotationId\": %d}".formatted(JsonPath.<Integer>read(quotation, "$.id"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var transactionId = UUID.fromString(JsonPath.read(separation, "$.transactionId"));
        var voucherId = UUID.randomUUID();
        var evidenceId = reservationCommandService.handle(new ReceiveVoucherEvidenceCommand(transactionId, voucherId,
                Money.of(new BigDecimal("9000")), LocalDate.now().minusDays(1), "TRX-1", false,
                "vouchers/%s/%s.jpg".formatted(transactionId, voucherId), Instant.now())).getId();
        mockMvc.perform(post("/api/v1/verifications/{id}/approve", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L)))
                .andExpect(status().isOk());

        var pdf = ("%PDF-1.7 contrato " + transactionId + " ").repeat(60).getBytes(StandardCharsets.US_ASCII);
        var documentId = UUID.randomUUID();
        var body = "{\"documentId\": \"%s\", \"sizeBytes\": %d}".formatted(documentId, pdf.length);
        var answer = mockMvc.perform(post("/api/v1/reservations/{id}/contract/upload-url", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var upload = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(answer, "$.uploadUrl")))
                        .header("Content-Type", "application/pdf")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(pdf))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, upload.statusCode(), upload.body());
        var issued = mockMvc.perform(post("/api/v1/reservations/{id}/contract", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.documentSha256").value(SignedContractDocument.sha256Of(pdf)))
                .andReturn().getResponse().getContentAsString();
        return new Issued(JsonPath.<Number>read(issued, "$.id").longValue(), transactionId, pdf);
    }

    /** A lot of its own published project, with a 20 % minimum down payment. */
    private Lot lot(String code) {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Firma electrónica", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, code,
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
