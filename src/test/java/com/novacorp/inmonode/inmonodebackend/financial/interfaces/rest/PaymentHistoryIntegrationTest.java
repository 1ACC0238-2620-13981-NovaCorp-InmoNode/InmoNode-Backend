package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.jayway.jsonpath.JsonPath;
import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
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
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The payment history of a reservation (US-25), end to end over HTTP against a real PostgreSQL and a real
 * S3-compatible storage: a buyer separates a lot and sends a voucher, the back office rejects it, the buyer sees why
 * and sends a substitute, the back office approves it and the buyer downloads it. Each test uses its own buyers and lot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class PaymentHistoryIntegrationTest {

    private static final AtomicLong BUYERS = new AtomicLong(11_000);
    private static final LocalDate YESTERDAY = LocalDate.now(ZoneId.of("America/Lima")).minusDays(1);

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LotRepository lotRepository;

    @Test
    void aBuyerSeesTheRejectionSendsASubstituteAndDownloadsItOnceApproved() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var transactionId = separate(buyer, lot());
        var illegible = new byte[2048];
        sendVoucher(buyer, UUID.randomUUID(), transactionId, illegible).andExpect(status().isCreated());

        var rejectedId = evidenceIdAt(history(buyer, transactionId), 0);
        decide(rejectedId, "reject", "{\"reason\": \"Voucher ilegible\"}").andExpect(status().isOk());

        var afterRejection = historyOf(buyer, transactionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.waitingUntil", notNullValue()))
                .andExpect(jsonPath("$.evidences", hasSize(1)))
                .andExpect(jsonPath("$.evidences[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.evidences[0].rejectionReason").value("Voucher ilegible"))
                .andExpect(jsonPath("$.evidences[0].downloadUrl", nullValue()))
                .andReturn().getResponse().getContentAsString();
        assertTrue(Instant.parse(JsonPath.read(afterRejection, "$.waitingUntil")).isAfter(Instant.now()));

        var legible = new byte[3072];
        legible[0] = 7;
        sendVoucher(buyer, UUID.randomUUID(), transactionId, legible).andExpect(status().isCreated());
        historyOf(buyer, transactionId)
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.waitingUntil", nullValue()))
                .andExpect(jsonPath("$.evidences[1].status").value("PENDING"));
        var substituteId = evidenceIdAt(history(buyer, transactionId), 1);
        decide(substituteId, "approve", "{\"note\": \"Conciliado\"}").andExpect(status().isOk());

        var approved = historyOf(buyer, transactionId)
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.evidences[1].status").value("APPROVED"))
                .andExpect(jsonPath("$.evidences[1].rejectionReason", nullValue()))
                .andExpect(jsonPath("$.evidences[1].downloadExpiresAt", notNullValue()))
                .andExpect(jsonPath("$.evidences[0].downloadUrl", nullValue()))
                .andReturn().getResponse().getContentAsString();
        var download = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(approved,
                "$.evidences[1].downloadUrl"))).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, download.statusCode());
        assertArrayEquals(legible, download.body(), "the approved voucher, not the rejected one");
    }

    @Test
    void onlyThePersonWhoMadeTheReservationSeesItsPayments() throws Exception {
        var buyer = BUYERS.incrementAndGet();
        var otherBuyer = BUYERS.incrementAndGet();
        var transactionId = separate(buyer, lot());

        historyOf(buyer, transactionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("WEB"))
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.initialAmount").value(9000.0))
                .andExpect(jsonPath("$.evidences", hasSize(0)));
        historyOf(otherBuyer, transactionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        historyOf(buyer, UUID.randomUUID()).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/reservations/{id}/payment-evidences", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reservations/{id}/payment-evidences", transactionId))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions historyOf(long buyer, UUID transactionId) throws Exception {
        return mockMvc.perform(get("/api/v1/reservations/{id}/payment-evidences", transactionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer)));
    }

    private String history(long buyer, UUID transactionId) throws Exception {
        return historyOf(buyer, transactionId).andReturn().getResponse().getContentAsString();
    }

    private static long evidenceIdAt(String history, int index) {
        return JsonPath.<Number>read(history, "$.evidences[%d].id".formatted(index)).longValue();
    }

    private ResultActions decide(long evidenceId, String decision, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/verifications/{id}/" + decision, evidenceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.FINANCE_ADMIN, 77L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
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

    /** Asks for the upload URL, uploads the file with it and registers the voucher typed in the portal. */
    private ResultActions sendVoucher(long buyer, UUID voucherId, UUID transactionId, byte[] file) throws Exception {
        var answer = mockMvc.perform(post("/api/v1/vouchers/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"voucherId": "%s", "reservationId": "%s", "contentType": "image/jpeg",
                                 "sizeBytes": %d}""".formatted(voucherId, transactionId, file.length)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var upload = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(answer, "$.uploadUrl")))
                        .header("Content-Type", "image/jpeg")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(file))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, upload.statusCode(), upload.body());
        return mockMvc.perform(post("/api/v1/vouchers")
                .header(HttpHeaders.AUTHORIZATION, bearer(Role.BUYER, buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"voucherId": "%s", "reservationId": "%s", "contentType": "image/jpeg", "sizeBytes": %d,
                         "amount": 9000, "currency": "PEN", "operationDate": "%s", "operationCode": "TRX-1",
                         "manuallyCorrected": false}""".formatted(voucherId, transactionId, file.length, YESTERDAY)));
    }

    /** A lot of its own published project, with a 20 % minimum down payment. */
    private Lot lot() {
        var rules = new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120, BigDecimal.ONE);
        var project = Project.create("Historial de pagos", "Chilca", null, null, rules);
        project.publish(1);
        var projectId = projectRepository.save(project).getId();
        var boundary = LotBoundary.fromPolygonRings(List.of(List.of(
                List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.0))));
        return lotRepository.saveAll(List.of(Lot.register(projectId, "H-01",
                new LotDimensions(new BigDecimal("120"), null, null), Money.of(new BigDecimal("45000")), boundary)))
                .getFirst();
    }

    private String bearer(Role role, long userId) {
        var user = User.restore(userId, role.name().toLowerCase() + userId + "@mail.com", "hash", role,
                UserStatus.ACTIVE, null, null, 0, null);
        return "Bearer " + tokenService.generateToken(user);
    }
}
