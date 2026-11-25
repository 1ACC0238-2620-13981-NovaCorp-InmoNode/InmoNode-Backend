package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureInvitation;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureProvider;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureSheet;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature.SignatureSubmission;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveVoucherEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.SendDueSignatureRequestsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureEventOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureEventRecord;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignedContractDocument;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.SignatureEventRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ContractSignatureCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.signature.docuseal.DocuSealCallbacks;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T91: DocuSeal's callbacks of one signature arriving at the same time, and two runs of the job at once, against a
 * real PostgreSQL and a real S3-compatible storage. DocuSeal sends form.completed and submission.completed within
 * milliseconds of each other, and may redeliver either. Whatever the order, the contract is signed once, the lot
 * sold once, legal told once, and the outcome kept for each callback is the one it answered. Only DocuSeal's API is
 * mocked; the callbacks are signed with the test-only secret of the test profile and verified for real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class ContractSignatureConcurrencyIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_docuseal-test-only-secret";
    private static final String WEBHOOK = "/api/v1/webhooks/docuseal";
    private static final AtomicLong BUYERS = new AtomicLong(53_000);
    private static final AtomicLong SUBMISSIONS = new AtomicLong(90_000);

    private final HttpClient http = HttpClient.newHttpClient();

    @MockitoBean
    private SignatureProvider signatureProvider;

    @MockitoSpyBean
    private SignatureEventRepository events;

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
        // Only this test's requests are due, so the job sends nothing another test left behind.
        jdbc.update("""
                UPDATE financial_document_control.contract_signature_requests
                SET next_attempt_at = now() + interval '1 day' WHERE status = 'PENDING'
                """);
    }

    @RepeatedTest(3)
    void bothCompletionEventsAndARedeliveryAtOnceSignTheContractOnce() throws Exception {
        var lot = lot("C-01");
        var contractId = issue(BUYERS.incrementAndGet(), lot);
        var submission = sendToSignature(contractId);
        confirmedByDocuSeal(contractId, submission);
        var reference = "inmonode-contract-" + contractId;
        var form = DocuSealCallbacks.form("form.completed", submission, reference, null);
        var whole = DocuSealCallbacks.submission("submission.completed", submission, reference);

        var answers = atOnce(List.of(() -> callback(form), () -> callback(whole), () -> callback(form)));

        assertAll(answers.stream().map(answer -> () -> assertEquals(200, answer.getStatus())));
        var contract = contractRepository.findById(contractId).orElseThrow();
        assertEquals(ContractStatus.SIGNED, contract.getStatus());
        assertEquals(LotStatus.SOLD, lotRepository.findById(lot.getId()).orElseThrow().getStatus());
        assertEquals(1, countOf("notification_outbox WHERE event_key = ?", "contract-signed:" + contractId),
                "legal is told once");
        var formOutcome = outcomeOf("form.completed:" + submission);
        var wholeOutcome = outcomeOf("submission.completed:" + submission);
        assertEquals(List.of("DUPLICATE", "SIGNED"), List.of(formOutcome, wholeOutcome).stream().sorted().toList(),
                "one callback signed it and the other found it signed");
        assertEquals(formOutcome, outcomeOf(answers.get(0)), "form.completed answers what it kept");
        assertEquals(wholeOutcome, outcomeOf(answers.get(1)), "submission.completed answers what it kept");
        assertEquals(formOutcome, outcomeOf(answers.get(2)), "a redelivery answers its first outcome");
        var stored = http.send(HttpRequest.newBuilder(URI.create(signedDownloadUrl(contract))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(contract.getSignedDocument().sha256(), SignedContractDocument.sha256Of(stored.body()),
                "the stored sheet is the one whose hash was kept");
    }

    /**
     * Two deliveries of form.completed: the first signs the contract and is slow to log it, as a pause of the JVM
     * after its commit would make it, and the second finds the contract signed. The log must keep SIGNED, the
     * outcome of the delivery that signed it, and both must answer it.
     */
    @Test
    void aCallbackKeepsTheOutcomeOfTheDeliveryThatSignedItEvenIfItIsSlowToLogIt() throws Exception {
        var contractId = issue(BUYERS.incrementAndGet(), lot("S-01"));
        var submission = sendToSignature(contractId);
        confirmedByDocuSeal(contractId, submission);
        var form = DocuSealCallbacks.form("form.completed", submission, "inmonode-contract-" + contractId, null);
        doAnswer(invocation -> {
            Thread.sleep(800);
            return invocation.callRealMethod();
        }).when(events).record(argThat((SignatureEventRecord event) -> event.outcome() == SignatureEventOutcome.SIGNED));

        var answers = atOnce(List.of(() -> callback(form), () -> {
            Thread.sleep(150);
            return callback(form);
        }));

        assertEquals(List.of("SIGNED", "SIGNED"), List.of(outcomeOf(answers.get(0)), outcomeOf(answers.get(1))));
        assertEquals("SIGNED", outcomeOf("form.completed:" + submission));
        assertEquals(ContractStatus.SIGNED, contractRepository.findById(contractId).orElseThrow().getStatus());
    }

    @RepeatedTest(3)
    void twoRunsOfTheJobAtOnceSendEachContractOnce() throws Exception {
        var contracts = List.of(issue(BUYERS.incrementAndGet(), lot("J-01")), issue(BUYERS.incrementAndGet(), lot("J-02")),
                issue(BUYERS.incrementAndGet(), lot("J-03")));
        // DocuSeal answers slowly, so both runs see the same requests due.
        when(signatureProvider.requestSignature(any())).thenAnswer(invocation -> {
            Thread.sleep(300);
            var submission = SUBMISSIONS.incrementAndGet();
            return new SignatureInvitation(submission, "https://firma.test/s/slug-" + submission);
        });
        contracts.forEach(this::makeDue);

        var start = new CountDownLatch(1);
        int sent;
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> run = () -> {
                start.await();
                return signatures.handle(new SendDueSignatureRequestsCommand(20));
            };
            var first = executor.submit(run);
            var second = executor.submit(run);
            start.countDown();
            sent = first.get(60, TimeUnit.SECONDS) + second.get(60, TimeUnit.SECONDS);
        }

        assertEquals(3, sent, "the two runs sent three contracts between them");
        var sheets = ArgumentCaptor.forClass(SignatureSheet.class);
        verify(signatureProvider, times(3)).requestSignature(sheets.capture());
        assertEquals(new HashSet<>(contracts), new HashSet<>(sheets.getAllValues().stream()
                .map(SignatureSheet::contractId).toList()), "each contract went to DocuSeal once");
        var kept = new HashSet<Long>();
        for (var contractId : contracts) {
            var contract = contractRepository.findById(contractId).orElseThrow();
            assertEquals(ContractStatus.SIGNATURE_REQUESTED, contract.getStatus());
            assertEquals("SENT", jdbc.queryForObject("SELECT status FROM financial_document_control."
                    + "contract_signature_requests WHERE contract_id = ?", String.class, contractId));
            kept.add(contract.getSignatureSubmissionId());
        }
        assertEquals(3, kept.size(), "each contract keeps the one submission DocuSeal opened for it");
    }

    // ---------------------------------------------------------------- helpers

    private static List<MockHttpServletResponse> atOnce(List<Callable<MockHttpServletResponse>> calls) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(calls.size())) {
            var futures = calls.stream().map(call -> executor.submit(() -> {
                start.await();
                return call.call();
            })).toList();
            start.countDown();
            var answers = new ArrayList<MockHttpServletResponse>();
            for (var future : futures) {
                answers.add(future.get(60, TimeUnit.SECONDS));
            }
            return answers;
        }
    }

    private void confirmedByDocuSeal(long contractId, long submission) {
        when(signatureProvider.fetchSubmission(anyLong())).thenReturn(new SignatureSubmission(submission,
                SignatureSubmission.Status.COMPLETED, "inmonode-contract-" + contractId,
                Instant.parse("2026-11-22T21:31:55.166Z"), null, "http://docuseal.test/file/signed.pdf"));
        when(signatureProvider.downloadSignedDocument(any())).thenReturn(
                ("%PDF-1.7 hoja firmada del contrato " + contractId).getBytes(StandardCharsets.US_ASCII));
    }

    private MockHttpServletResponse callback(String payload) throws Exception {
        return mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Docuseal-Signature",
                                DocuSealCallbacks.sign(payload, WEBHOOK_SECRET, Instant.now().getEpochSecond())))
                .andReturn().getResponse();
    }

    private static String outcomeOf(MockHttpServletResponse answer) throws Exception {
        return JsonPath.read(answer.getContentAsString(), "$.outcome");
    }

    private String outcomeOf(String eventKey) {
        return jdbc.queryForObject("SELECT outcome FROM financial_document_control.signature_provider_events "
                + "WHERE event_key = ?", String.class, eventKey);
    }

    private int countOf(String fromWhere, Object argument) {
        return jdbc.queryForObject("SELECT count(*) FROM financial_document_control." + fromWhere, Integer.class,
                argument);
    }

    private String signedDownloadUrl(Contract contract) throws Exception {
        var answer = mockMvc.perform(get("/api/v1/reservations/{id}/contract", contract.getTransactionId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, contract.getBuyerId())))
                .andReturn().getResponse();
        assertEquals(200, answer.getStatus(), answer.getContentAsString());
        return JsonPath.read(answer.getContentAsString(), "$.signedDownloadUrl");
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

    /** Quote, separation, voucher, approval and contract, as the buyer and the back office do it. */
    private long issue(long buyer, Lot lot) throws Exception {
        var quotation = mockMvc.perform(post("/api/v1/lots/{lotId}/quotations", lot.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialPayment\": 9000, \"termMonths\": 12}"))
                .andReturn().getResponse();
        assertEquals(201, quotation.getStatus(), quotation.getContentAsString());
        var separation = mockMvc.perform(post("/api/v1/lots/{lotId}/separation-requests", lot.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quotationId\": %d}".formatted(
                                JsonPath.<Integer>read(quotation.getContentAsString(), "$.id"))))
                .andReturn().getResponse();
        assertEquals(201, separation.getStatus(), separation.getContentAsString());
        var transactionId = UUID.fromString(JsonPath.read(separation.getContentAsString(), "$.transactionId"));
        var voucherId = UUID.randomUUID();
        var evidenceId = reservationCommandService.handle(new ReceiveVoucherEvidenceCommand(transactionId, voucherId,
                Money.of(new BigDecimal("9000")), LocalDate.now().minusDays(1), "TRX-1", false,
                "vouchers/%s/%s.jpg".formatted(transactionId, voucherId), Instant.now())).getId();
        var approved = mockMvc.perform(post("/api/v1/verifications/{id}/approve", evidenceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L))).andReturn().getResponse();
        assertEquals(200, approved.getStatus(), approved.getContentAsString());

        var pdf = ("%PDF-1.7 contrato " + transactionId + " ").repeat(60).getBytes(StandardCharsets.US_ASCII);
        var documentId = UUID.randomUUID();
        var body = "{\"documentId\": \"%s\", \"sizeBytes\": %d}".formatted(documentId, pdf.length);
        var url = mockMvc.perform(post("/api/v1/reservations/{id}/contract/upload-url", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
        assertEquals(200, url.getStatus(), url.getContentAsString());
        var upload = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(url.getContentAsString(), "$.uploadUrl")))
                        .header("Content-Type", "application/pdf")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(pdf))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, upload.statusCode(), upload.body());
        var issued = mockMvc.perform(post("/api/v1/reservations/{id}/contract", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
        assertEquals(201, issued.getStatus(), issued.getContentAsString());
        return JsonPath.<Number>read(issued.getContentAsString(), "$.id").longValue();
    }

    /** A lot of its own published project, with a 20 % minimum down payment. */
    private Lot lot(String code) {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Firmas simultáneas", "Chilca", null, null, rules);
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
