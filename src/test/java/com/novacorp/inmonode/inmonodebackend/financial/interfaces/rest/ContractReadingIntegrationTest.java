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
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A buyer follows the contract of their web separation (US-21, US-22) end to end over HTTP, against a real PostgreSQL
 * and a real S3-compatible storage: quote, separate, payment, verification, issuing, reading and agreement. The
 * voucher reaches financial through the command its listener uses; each test uses its own buyers and lot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class ContractReadingIntegrationTest {

    private static final AtomicLong BUYERS = new AtomicLong(12_000);

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
    private ReservationCommandService reservationCommandService;

    @Test
    void aBuyerFollowsTheContractFromPreparationToTheirAgreement() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var transactionId = separate(buyer, lot());

        contractOf(buyer, transactionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("NOT_AVAILABLE"))
                .andExpect(jsonPath("$.contractId", nullValue()));
        var evidenceId = sendVoucher(transactionId);
        contractOf(buyer, transactionId).andExpect(jsonPath("$.availability").value("IN_PREPARATION"));
        approve(evidenceId);
        contractOf(buyer, transactionId)
                .andExpect(jsonPath("$.availability").value("IN_PREPARATION"))
                .andExpect(jsonPath("$.downloadUrl", nullValue()));

        var pdf = pdf();
        issue(transactionId, pdf);
        var issued = contractOf(buyer, transactionId)
                .andExpect(jsonPath("$.availability").value("ISSUED"))
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.contractId", notNullValue()))
                .andExpect(jsonPath("$.issuedAt", notNullValue()))
                .andExpect(jsonPath("$.downloadExpiresAt", notNullValue()))
                .andExpect(jsonPath("$.acknowledgedAt", nullValue()))
                .andReturn().getResponse().getContentAsString();
        var download = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(issued, "$.downloadUrl"))).GET()
                .build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, download.statusCode());
        assertArrayEquals(pdf, download.body());

        var contractId = JsonPath.<Number>read(issued, "$.contractId").longValue();
        var first = acknowledge(buyer, contractId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(contractId))
                .andExpect(jsonPath("$.acknowledgedAt", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        var agreedAt = JsonPath.<String>read(first, "$.acknowledgedAt");
        acknowledge(buyer, contractId).andExpect(jsonPath("$.acknowledgedAt").value(agreedAt));
        contractOf(buyer, transactionId).andExpect(jsonPath("$.acknowledgedAt").value(agreedAt));
    }

    @Test
    void anotherBuyerCannotReadOrAgreeToTheContract() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var otherBuyer = BUYERS.incrementAndGet();
        var transactionId = separate(buyer, lot());
        approve(sendVoucher(transactionId));
        issue(transactionId, pdf());
        var contractId = JsonPath.<Number>read(contractOf(buyer, transactionId).andReturn().getResponse()
                .getContentAsString(), "$.contractId").longValue();

        contractOf(otherBuyer, transactionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        acknowledge(otherBuyer, contractId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
        acknowledge(buyer, 999_999L).andExpect(status().isNotFound());
        contractOf(buyer, transactionId).andExpect(jsonPath("$.acknowledgedAt", nullValue()));
    }

    @Test
    void onlyBuyersReadContracts() throws Exception {
        var transactionId = separate(BUYERS.incrementAndGet(), lot());

        mockMvc.perform(get("/api/v1/reservations/{id}/contract", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reservations/{id}/contract", transactionId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/contracts/{id}/acknowledgment", 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FIELD_AGENT, 7L)))
                .andExpect(status().isForbidden());
    }

    private ResultActions contractOf(long buyer, UUID transactionId) throws Exception {
        return mockMvc.perform(get("/api/v1/reservations/{id}/contract", transactionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)));
    }

    private ResultActions acknowledge(long buyer, long contractId) throws Exception {
        return mockMvc.perform(post("/api/v1/contracts/{id}/acknowledgment", contractId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)));
    }

    /** Simulates 9 000 down in 12 months and separates the lot; returns the transaction id. */
    private UUID separate(long buyer, Lot lot) throws Exception {
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
        return UUID.fromString(JsonPath.read(separation, "$.transactionId"));
    }

    private Long sendVoucher(UUID transactionId) {
        var voucherId = UUID.randomUUID();
        return reservationCommandService.handle(new ReceiveVoucherEvidenceCommand(transactionId, voucherId,
                Money.of(new BigDecimal("9000")), LocalDate.now().minusDays(1), "TRX-1", false,
                "vouchers/%s/%s.jpg".formatted(transactionId, voucherId), Instant.now())).getId();
    }

    private void approve(Long evidenceId) throws Exception {
        mockMvc.perform(post("/api/v1/verifications/{id}/approve", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L)))
                .andExpect(status().isOk());
    }

    /** The back office uploads the PDF with a presigned URL and issues the contract. */
    private void issue(UUID transactionId, byte[] pdf) throws Exception {
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
        mockMvc.perform(post("/api/v1/reservations/{id}/contract", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private static byte[] pdf() {
        var file = new byte[3000];
        System.arraycopy("%PDF-1.7".getBytes(), 0, file, 0, 8);
        file[2999] = 9;
        return file;
    }

    /** A lot of its own published project, with a 20 % minimum down payment. */
    private Lot lot() {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Lectura de contratos", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, "K-01",
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
