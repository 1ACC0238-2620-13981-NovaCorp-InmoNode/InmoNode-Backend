package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveVoucherEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.FieldReservationConsolidationFacade;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.LotAvailabilityFacade;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The back office uploads and issues the contract of a verified web reservation (US-21) over HTTP, against a real
 * PostgreSQL and a real S3-compatible storage. Reservations reach the verified state through the same commands the
 * other contexts and the verification queue use; each test uses its own lot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class ContractIssuingIntegrationTest {

    private static final long ISSUER = 77L;
    private static final long BUYER = 41L;

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationCommandService reservationCommandService;

    @Autowired
    private LotAvailabilityFacade lotAvailabilityFacade;

    @Autowired
    private FieldReservationConsolidationFacade fieldFacade;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private ObjectStorage objectStorage;

    @Test
    void theBackOfficeUploadsAndIssuesTheContractOfAVerifiedWebReservation() throws Exception {
        var lot = lot("C-01");
        var transactionId = verifiedWebReservation(lot);
        var documentId = UUID.randomUUID();
        var pdf = pdf(4096);

        var answer = requestUrl(transactionId, documentId, pdf.length)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("contracts/%s/%s.pdf".formatted(transactionId, documentId)))
                .andExpect(jsonPath("$.headers['Content-Type']").value("application/pdf"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(200, upload(answer, pdf, "application/pdf").statusCode());

        issue(transactionId, documentId, pdf.length)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.buyerId").value((int) BUYER))
                .andExpect(jsonPath("$.lotId").value(lot.getId()))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.issuedBy").value((int) ISSUER))
                .andExpect(jsonPath("$.issuedAt", notNullValue()))
                .andExpect(jsonPath("$.buyerAcknowledgedAt", nullValue()));

        var reservation = reservationRepository.findBySourceEventId(transactionId).orElseThrow();
        var contract = contractRepository.findByReservationId(reservation.getId()).orElseThrow();
        assertEquals(documentId, contract.getDocumentId());
        assertEquals(pdf.length, contract.getSizeBytes());
        assertEquals(pdf.length, objectStorage.describe(contract.getObjectKey()).orElseThrow().sizeBytes());
    }

    @Test
    void aSecondContractOfTheSameReservationIsAConflict() throws Exception {
        var transactionId = verifiedWebReservation(lot("C-02"));
        var documentId = UUID.randomUUID();
        var pdf = pdf(2048);
        upload(requestUrl(transactionId, documentId, pdf.length).andReturn().getResponse().getContentAsString(), pdf,
                "application/pdf");
        issue(transactionId, documentId, pdf.length).andExpect(status().isCreated());

        requestUrl(transactionId, UUID.randomUUID(), 2048)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_CONFLICT"));
        issue(transactionId, documentId, pdf.length).andExpect(status().isConflict());
    }

    @Test
    void aContractIsIssuedOnlyForAVerifiedWebReservation() throws Exception {
        var pending = webReservation(lot("C-03"));
        sendVoucher(pending, "9000");
        var field = UUID.randomUUID();
        fieldFacade.consolidate(field, lot("C-04").getId(), 7L, UUID.randomUUID(), new BigDecimal("1500"),
                Instant.now());
        approve(sendVoucher(field, "1500"));

        requestUrl(pending, UUID.randomUUID(), 2048)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.details").value(containsString("PENDING_VERIFICATION")));
        requestUrl(field, UUID.randomUUID(), 2048)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.details").value(containsString("field")));
        requestUrl(UUID.randomUUID(), UUID.randomUUID(), 2048)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void theContractCannotBeIssuedWithoutItsUploadedPdf() throws Exception {
        var transactionId = verifiedWebReservation(lot("C-05"));
        var documentId = UUID.randomUUID();

        issue(transactionId, documentId, 2048)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_FILE_NOT_UPLOADED"));

        var pdf = pdf(2048);
        upload(requestUrl(transactionId, documentId, pdf.length).andReturn().getResponse().getContentAsString(), pdf,
                "application/pdf");
        issue(transactionId, documentId, 4096)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_FILE_NOT_UPLOADED"));
        assertTrue(contractRepository.findByReservationId(
                reservationRepository.findBySourceEventId(transactionId).orElseThrow().getId()).isEmpty());
    }

    @Test
    void aPdfOverTenMegabytesOrAnotherTypeIsRefused() throws Exception {
        var transactionId = verifiedWebReservation(lot("C-06"));

        requestUrl(transactionId, UUID.randomUUID(), 10L * 1024 * 1024 + 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("sizeBytes")));
        var answer = requestUrl(transactionId, UUID.randomUUID(), 2048).andReturn().getResponse().getContentAsString();
        assertEquals(403, upload(answer, new byte[2048], "image/jpeg").statusCode(),
                "the signature fixes the PDF type");
    }

    @Test
    void onlyTheFinanceBackOfficeIssuesContracts() throws Exception {
        var transactionId = verifiedWebReservation(lot("C-07"));
        var body = "{\"documentId\": \"%s\", \"sizeBytes\": 2048}".formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/reservations/{id}/contract/upload-url", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, BUYER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/reservations/{id}/contract", transactionId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions requestUrl(UUID transactionId, UUID documentId, long sizeBytes) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations/{id}/contract/upload-url", transactionId)
                .header(HttpHeaders.AUTHORIZATION, finance())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\": \"%s\", \"sizeBytes\": %d}".formatted(documentId, sizeBytes)));
    }

    private ResultActions issue(UUID transactionId, UUID documentId, long sizeBytes) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations/{id}/contract", transactionId)
                .header(HttpHeaders.AUTHORIZATION, finance())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\": \"%s\", \"sizeBytes\": %d}".formatted(documentId, sizeBytes)));
    }

    private HttpResponse<String> upload(String answer, byte[] file, String contentType) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(answer, "$.uploadUrl")))
                        .header("Content-Type", contentType)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(file))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] pdf(int size) {
        var file = new byte[size];
        System.arraycopy("%PDF-1.7".getBytes(), 0, file, 0, 8);
        return file;
    }

    /** A web separation of 9 000 down whose voucher the back office approved. */
    private UUID verifiedWebReservation(Lot lot) throws Exception {
        var transactionId = webReservation(lot);
        approve(sendVoucher(transactionId, "9000"));
        return transactionId;
    }

    private UUID webReservation(Lot lot) {
        var transactionId = UUID.randomUUID();
        assertEquals("BLOCKED", lotAvailabilityFacade.blockLot(transactionId, lot.getId(), BUYER,
                new BigDecimal("9000"), "PEN", Instant.now()).result());
        return transactionId;
    }

    private Long sendVoucher(UUID reservationId, String amount) {
        var voucherId = UUID.randomUUID();
        return reservationCommandService.handle(new ReceiveVoucherEvidenceCommand(reservationId, voucherId,
                Money.of(new BigDecimal(amount)), LocalDate.now().minusDays(1), "00123456", false,
                "vouchers/%s/%s.jpg".formatted(reservationId, voucherId), Instant.now())).getId();
    }

    private void approve(Long evidenceId) throws Exception {
        mockMvc.perform(post("/api/v1/verifications/{id}/approve", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, finance()))
                .andExpect(status().isOk());
    }

    /** A lot of its own published project. */
    private Lot lot(String code) {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Contratos", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, code,
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String finance() {
        return bearer(Role.FINANCE_ADMIN, ISSUER);
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
